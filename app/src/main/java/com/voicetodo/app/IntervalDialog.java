package com.voicetodo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 알림 간격 + 첫 알림 날짜·시간을 고르는 창.
 *  - 자주 쓰는 간격 버튼 (30초 ~ 매달)
 *  - 직접 설정: 숫자 + 단위(초/분/시간/일/주/개월) 휠, 숫자는 직접 입력도 가능
 *  - 첫 알림: 달력 → 시계로 날짜·시간 지정, 또는 "등록 시점부터"
 *  - 미리보기: 다음 알림 3회 시각
 */
public final class IntervalDialog {

    public interface Callback {
        /** once=true 면 한 번만 알림 (interval 은 '얼마 뒤') */
        void onSave(Interval interval, long startAt, boolean once);
    }

    private static final String[] UNITS = {
            Interval.SEC, Interval.MIN, Interval.HOUR, Interval.DAY, Interval.WEEK, Interval.MONTH};
    private static final String[] UNIT_LABELS = {"초", "분", "시간", "일", "주", "개월"};
    /** 단위별 숫자 범위 */
    private static final int[][] RANGES = {{10, 59}, {1, 59}, {1, 99}, {1, 99}, {1, 52}, {1, 24}};

    private static final Interval[] PRESETS = {
            Interval.of(30, Interval.SEC), Interval.of(1, Interval.MIN),
            Interval.of(5, Interval.MIN), Interval.of(10, Interval.MIN),
            Interval.of(30, Interval.MIN), Interval.of(1, Interval.HOUR),
            Interval.of(3, Interval.HOUR), Interval.of(6, Interval.HOUR),
            Interval.of(12, Interval.HOUR), Interval.of(1, Interval.DAY),
            Interval.of(1, Interval.WEEK), Interval.of(1, Interval.MONTH)};
    private static final String[] PRESET_LABELS = {
            "30초", "1분", "5분", "10분", "30분", "1시간", "3시간", "6시간", "12시간", "매일", "매주", "매달"};

    private final Activity act;
    private final String startEmptyLabel;
    private final List<TextView> chips = new ArrayList<>();
    private NumberPicker amountPicker;
    private NumberPicker unitPicker;
    private TextView startValue;
    private TextView preview;
    private TextView suffix;
    private CheckBox onceCheck;
    private long startAt;

    private IntervalDialog(Activity act, String startEmptyLabel) {
        this.act = act;
        this.startEmptyLabel = startEmptyLabel;
    }

    /**
     * @param startAt 처음 보여줄 첫 알림 시각 (0 = 등록 시점부터 / 지금부터)
     * @param startEmptyLabel startAt 이 0 일 때 표시할 문구
     */
    public static void show(Activity act, String title, Interval current, boolean once, long startAt,
                            String startEmptyLabel, Callback cb) {
        new IntervalDialog(act, startEmptyLabel).open(title, current, once, startAt, cb);
    }

    private void open(String title, Interval current, boolean once, long initialStart, Callback cb) {
        View v = act.getLayoutInflater().inflate(R.layout.dialog_interval, null);
        amountPicker = v.findViewById(R.id.picker_amount);
        unitPicker = v.findViewById(R.id.picker_unit);
        startValue = v.findViewById(R.id.start_value);
        preview = v.findViewById(R.id.preview);
        suffix = v.findViewById(R.id.suffix);
        onceCheck = v.findViewById(R.id.check_once);
        onceCheck.setChecked(once);
        onceCheck.setOnCheckedChangeListener((b, c) -> onChanged());
        startAt = initialStart;

        buildPresets(v.findViewById(R.id.preset_container));

        unitPicker.setMinValue(0);
        unitPicker.setMaxValue(UNITS.length - 1);
        unitPicker.setDisplayedValues(UNIT_LABELS);
        unitPicker.setWrapSelectorWheel(false);
        amountPicker.setWrapSelectorWheel(false);

        setPickers(current);

        unitPicker.setOnValueChangedListener((p, o, n) -> {
            applyRange(n);
            onChanged();
        });
        amountPicker.setOnValueChangedListener((p, o, n) -> onChanged());
        // 키보드로 숫자를 직접 고친 경우도 반영
        EditText amountInput = findEditText(amountPicker);
        if (amountInput != null) {
            amountInput.setOnFocusChangeListener((x, has) -> { if (!has) onChanged(); });
        }

        v.findViewById(R.id.btn_pick_start).setOnClickListener(x -> pickDate());
        v.findViewById(R.id.btn_reset_start).setOnClickListener(x -> {
            startAt = 0;
            onChanged();
        });

        onChanged();

        new AlertDialog.Builder(act)
                .setTitle(title)
                .setView(v)
                .setPositiveButton("저장", (d, w) -> {
                    amountPicker.clearFocus();   // 입력 중인 숫자 확정
                    cb.onSave(currentInterval(), startAt, onceCheck.isChecked());
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ---------- 프리셋 ----------

    private void buildPresets(LinearLayout container) {
        float d = act.getResources().getDisplayMetrics().density;
        int perRow = 4;
        LinearLayout row = null;
        for (int i = 0; i < PRESETS.length; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) rp.topMargin = (int) (8 * d);
                container.addView(row, rp);
            }
            TextView chip = new TextView(act);
            chip.setText(PRESET_LABELS[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(14);
            chip.setTextColor(act.getColorStateList(R.color.chip_text));
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (38 * d), 1f);
            if (i % perRow != perRow - 1) lp.setMarginEnd((int) (6 * d));
            final Interval preset = PRESETS[i];
            chip.setOnClickListener(x -> {
                setPickers(preset);
                onChanged();
            });
            row.addView(chip, lp);
            chips.add(chip);
        }
    }

    // ---------- 휠 ----------

    private void setPickers(Interval iv) {
        int u = 2;
        for (int i = 0; i < UNITS.length; i++) if (UNITS[i].equals(iv.unit)) u = i;
        unitPicker.setValue(u);
        applyRange(u);
        amountPicker.setValue(Math.max(RANGES[u][0], Math.min(RANGES[u][1], iv.amount)));
    }

    private void applyRange(int unitIndex) {
        amountPicker.setMinValue(RANGES[unitIndex][0]);
        amountPicker.setMaxValue(RANGES[unitIndex][1]);
    }

    private Interval currentInterval() {
        return Interval.of(amountPicker.getValue(), UNITS[unitPicker.getValue()]);
    }

    // ---------- 첫 알림 날짜·시간 ----------

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        if (startAt > 0) c.setTimeInMillis(startAt);
        DatePickerDialog dp = new DatePickerDialog(act, (view, y, m, day) -> pickTime(y, m, day),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.show();
    }

    private void pickTime(int y, int m, int day) {
        Calendar c = Calendar.getInstance();
        if (startAt > 0) c.setTimeInMillis(startAt);
        new TimePickerDialog(act, (view, h, min) -> {
            Calendar sel = Calendar.getInstance();
            sel.set(y, m, day, h, min, 0);
            sel.set(Calendar.MILLISECOND, 0);
            if (sel.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(act, "지금 이후 시각을 골라 주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            startAt = sel.getTimeInMillis();
            onChanged();
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
    }

    // ---------- 화면 갱신 ----------

    private void onChanged() {
        Interval iv = currentInterval();
        boolean once = onceCheck.isChecked();
        for (int i = 0; i < chips.size(); i++) chips.get(i).setSelected(PRESETS[i].equals(iv));
        suffix.setText(once ? "뒤에" : "마다");

        startValue.setText(startAt > 0 ? TimeFmt.format(startAt) : startEmptyLabel);

        long now = System.currentTimeMillis();
        if (once) {
            long at = Reminders.firstTrigger(true, iv, startAt, now);
            preview.setText("⏰ 한 번만 알림\n알림 시각  " + TimeFmt.format(at)
                    + (startAt > now ? "\n(날짜·시간을 지정해서 위 간격은 쓰지 않아요)" : ""));
            return;
        }
        long first = Reminders.firstTrigger(iv, startAt, now);
        long second = iv.addTo(first);
        long third = iv.addTo(second);
        preview.setText("🔔 " + iv.label() + "\n다음 알림  " + TimeFmt.format(first)
                + "\n그다음      " + TimeFmt.format(second)
                + "\n그다음      " + TimeFmt.format(third));
    }

    private static EditText findEditText(NumberPicker p) {
        for (int i = 0; i < p.getChildCount(); i++) {
            if (p.getChildAt(i) instanceof EditText) return (EditText) p.getChildAt(i);
        }
        return null;
    }
}
