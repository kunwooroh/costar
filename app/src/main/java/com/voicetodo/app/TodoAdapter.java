package com.voicetodo.app;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 할 일 목록 한 줄씩 그리기. */
public class TodoAdapter extends BaseAdapter {

    public interface Listener {
        void onToggleDone(Todo todo, boolean done);
        void onDelete(Todo todo);
        void onEditInterval(Todo todo);
        /** 길게 누르면 할 일을 소리내어 읽기 */
        void onSpeak(Todo todo);
    }

    private final LayoutInflater inflater;
    private final Listener listener;
    private List<Todo> items = new ArrayList<>();

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("a h:mm", Locale.KOREA);
    private final SimpleDateFormat dayTimeFmt = new SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA);

    public TodoAdapter(Context ctx, Listener listener) {
        this.inflater = LayoutInflater.from(ctx);
        this.listener = listener;
    }

    public void setItems(List<Todo> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Todo getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return items.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView
                : inflater.inflate(R.layout.item_todo, parent, false);
        Todo t = getItem(position);

        CheckBox check = v.findViewById(R.id.check_done);
        TextView text = v.findViewById(R.id.text_todo);
        TextView meta = v.findViewById(R.id.text_meta);
        ImageButton delete = v.findViewById(R.id.btn_delete);

        text.setText(t.text);
        if (t.done) {
            text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            text.setAlpha(0.45f);
            meta.setText("완료됨");
        } else {
            text.setPaintFlags(text.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            text.setAlpha(1f);
            String label = "🔔 " + Reminders.intervalLabel(t.intervalHours);
            if (t.nextAt > 0) label += " · 다음 " + formatTime(t.nextAt);
            meta.setText(label);
        }

        check.setOnCheckedChangeListener(null);
        check.setChecked(t.done);
        check.setOnCheckedChangeListener((b, isChecked) -> listener.onToggleDone(t, isChecked));
        delete.setOnClickListener(x -> listener.onDelete(t));
        v.setOnClickListener(x -> {
            if (!t.done) listener.onEditInterval(t);
        });
        v.setOnLongClickListener(x -> {
            listener.onSpeak(t);
            return true;
        });
        return v;
    }

    private String formatTime(long millis) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        boolean sameDay = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
        return sameDay ? timeFmt.format(c.getTime()) : dayTimeFmt.format(c.getTime());
    }
}
