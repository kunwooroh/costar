package com.voicetodo.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements TodoAdapter.Listener {

    private static final int REQ_SPEECH = 100;
    private static final int REQ_NOTIF = 101;

    private TodoDb db;
    private TodoAdapter adapter;
    private ListView listView;
    private EditText input;
    private RadioGroup intervalGroup;
    private TextView subtitle;

    private final BroadcastReceiver changedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyWindowInsets();

        db = TodoDb.get(this);
        Reminders.createChannel(this);

        listView = findViewById(R.id.list);
        input = findViewById(R.id.input);
        intervalGroup = findViewById(R.id.interval_group);
        subtitle = findViewById(R.id.subtitle);

        adapter = new TodoAdapter(this, this);
        listView.setAdapter(adapter);
        listView.setEmptyView(findViewById(R.id.empty));

        Switch ttsSwitch = findViewById(R.id.switch_tts);
        ttsSwitch.setChecked(Speaker.isEnabled(this));
        ttsSwitch.setOnCheckedChangeListener((b, on) -> {
            Speaker.setEnabled(this, on);
            if (on) {
                Speaker.speak(this, "음성 알림을 켰어요", null);
            } else {
                toast("음성 알림을 껐어요 (알림은 계속 와요)");
            }
        });

        findViewById(R.id.btn_mic).setOnClickListener(v -> startVoice());
        findViewById(R.id.btn_add).setOnClickListener(v -> addFromInput());
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                addFromInput();
                return true;
            }
            return false;
        });

        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(ReminderReceiver.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(changedReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(changedReceiver, f);
        }
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(changedReceiver);
    }

    // ---------- 음성 입력 ----------

    private void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "할 일을 말하세요\n예) 3시간마다 물 마시기");
        try {
            startActivityForResult(i, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            toast("이 폰에서 음성 인식을 쓸 수 없어요. Google 앱(음성 검색)을 설치·업데이트해 주세요.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            toast("잘 못 들었어요. 다시 말해 주세요.");
            return;
        }
        addTodo(results.get(0));
    }

    // ---------- 추가 ----------

    private void addFromInput() {
        String s = input.getText().toString().trim();
        if (s.isEmpty()) {
            toast("할 일을 입력하거나 마이크를 눌러 말해 주세요.");
            return;
        }
        addTodo(s);
        input.setText("");
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void addTodo(String raw) {
        VoiceParser.Result r = VoiceParser.parse(raw);
        if (r.text.isEmpty()) return;
        int hours = r.intervalHours > 0 ? r.intervalHours : selectedInterval();
        if (r.intervalHours > 0) checkInterval(r.intervalHours);

        long id = db.insert(r.text, hours);
        Todo t = db.getById(id);
        if (t != null) Reminders.scheduleNext(this, t);
        refresh();
        toast("추가: " + r.text + " (" + Reminders.intervalLabel(hours) + ")");
    }

    private int selectedInterval() {
        int checked = intervalGroup.getCheckedRadioButtonId();
        if (checked == R.id.interval_3) return 3;
        if (checked == R.id.interval_9) return 9;
        if (checked == R.id.interval_24) return 24;
        return 6;
    }

    private void checkInterval(int hours) {
        int id;
        switch (hours) {
            case 3: id = R.id.interval_3; break;
            case 9: id = R.id.interval_9; break;
            case 24: id = R.id.interval_24; break;
            default: id = R.id.interval_6; break;
        }
        intervalGroup.check(id);
    }

    // ---------- 목록 동작 ----------

    @Override
    public void onToggleDone(Todo todo, boolean done) {
        db.setDone(todo.id, done);
        if (done) {
            Reminders.cancel(this, todo.id);
        } else {
            Todo fresh = db.getById(todo.id);
            if (fresh != null) Reminders.scheduleNext(this, fresh);
        }
        listView.post(this::refresh);
    }

    @Override
    public void onDelete(Todo todo) {
        new AlertDialog.Builder(this)
                .setTitle("삭제할까요?")
                .setMessage(todo.text)
                .setPositiveButton("삭제", (d, w) -> {
                    Reminders.cancel(this, todo.id);
                    db.delete(todo.id);
                    refresh();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    public void onEditInterval(Todo todo) {
        int[] values = Reminders.INTERVALS;
        String[] labels = new String[values.length];
        int current = 1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = Reminders.intervalLabel(values[i]);
            if (values[i] == todo.intervalHours) current = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("알림 간격 변경")
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    db.setInterval(todo.id, values[which]);
                    Todo fresh = db.getById(todo.id);
                    if (fresh != null) Reminders.scheduleNext(this, fresh);
                    refresh();
                    d.dismiss();
                    toast("지금부터 " + labels[which] + " 알려드릴게요");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    public void onSpeak(Todo todo) {
        Speaker.speak(this, todo.text, null);
    }

    private void refresh() {
        List<Todo> all = db.getAll();
        adapter.setItems(all);
        int open = 0;
        for (Todo t : all) if (!t.done) open++;
        subtitle.setText(all.isEmpty() ? "마이크를 누르고 할 일을 말해 보세요"
                : "남은 할 일 " + open + "개 · 전체 " + all.size() + "개");
    }

    // ---------- 권한 / 화면 ----------

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF && (grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            toast("알림을 허용해야 할 일을 알려드릴 수 있어요. 설정 > 앱 > 음성 투두 > 알림에서 켜 주세요.");
        }
    }

    /** Android 15 의 전체 화면(edge-to-edge) 에서 상태바·내비게이션바·키보드에 가리지 않게 여백 적용. */
    private void applyWindowInsets() {
        View root = findViewById(R.id.root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(b.left, b.top, b.right, b.bottom);
                return WindowInsets.CONSUMED;
            });
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
