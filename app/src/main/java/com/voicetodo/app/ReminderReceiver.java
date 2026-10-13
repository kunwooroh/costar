package com.voicetodo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 알람이 울리면 알림을 띄우고 다음 알람을 예약. 알림의 "완료" 버튼도 처리. */
public class ReminderReceiver extends BroadcastReceiver {

    /** 화면이 열려 있으면 목록을 새로 고치도록 앱 내부에 알리는 액션. */
    public static final String ACTION_CHANGED = "com.voicetodo.app.action.CHANGED";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra(Reminders.EXTRA_ID, -1);
        if (id < 0) return;
        TodoDb db = TodoDb.get(ctx);
        Todo todo = db.getById(id);
        String action = intent.getAction();

        if (Reminders.ACTION_REMIND.equals(action)) {
            if (todo == null || todo.done) return;
            Reminders.showNotification(ctx, todo);
            Reminders.scheduleNext(ctx, todo);
            notifyChanged(ctx);
            // 알림과 함께 할 일을 소리내어 읽기
            if (Speaker.isEnabled(ctx) && Speaker.canSpeakNow(ctx)) {
                PendingResult pending = goAsync();
                Speaker.speak(ctx, "할 일 알림. " + todo.text, pending::finish);
            }
        } else if (Reminders.ACTION_DONE.equals(action)) {
            if (todo != null) db.setDone(id, true);
            Reminders.cancel(ctx, id);
            notifyChanged(ctx);
        }
    }

    private static void notifyChanged(Context ctx) {
        ctx.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(ctx.getPackageName()));
    }
}
