package com.voicetodo.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.util.List;

/** 알람 예약/취소와 알림 표시를 담당. */
public final class Reminders {

    public static final String CHANNEL_ID = "todo_reminders";
    public static final String ACTION_REMIND = "com.voicetodo.app.action.REMIND";
    public static final String ACTION_DONE = "com.voicetodo.app.action.DONE";
    public static final String EXTRA_ID = "todo_id";

    public static final int[] INTERVALS = {3, 6, 9, 24};
    private static final long HOUR = 60L * 60L * 1000L;

    private Reminders() {}

    public static String intervalLabel(int hours) {
        return hours >= 24 ? "하루마다" : hours + "시간마다";
    }

    public static void createChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "할 일 알림", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("등록한 할 일을 정해진 간격마다 알려줍니다");
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** 지금부터 간격만큼 뒤에 다음 알림을 예약. */
    public static void scheduleNext(Context ctx, Todo todo) {
        scheduleAt(ctx, todo.id, System.currentTimeMillis() + todo.intervalHours * HOUR);
    }

    public static void scheduleAt(Context ctx, long id, long triggerAt) {
        TodoDb.get(ctx).setNextAt(id, triggerAt);
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = alarmIntent(ctx, id);
        boolean exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        if (exactOk) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    public static void cancel(Context ctx, long id) {
        ctx.getSystemService(AlarmManager.class).cancel(alarmIntent(ctx, id));
        ctx.getSystemService(NotificationManager.class).cancel(notifId(id));
    }

    /** 재부팅/업데이트 후 미완료 할 일의 알람을 모두 다시 등록. */
    public static void rescheduleAll(Context ctx) {
        long now = System.currentTimeMillis();
        List<Todo> active = TodoDb.get(ctx).getActive();
        for (Todo t : active) {
            if (t.nextAt > now) {
                scheduleAt(ctx, t.id, t.nextAt);
            } else {
                // 꺼져 있는 동안 놓친 알림은 1분 뒤에 알려줌
                scheduleAt(ctx, t.id, now + 60_000L);
            }
        }
    }

    public static void showNotification(Context ctx, Todo todo) {
        Intent open = new Intent(ctx, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(ctx, notifId(todo.id), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent done = new Intent(ctx, ReminderReceiver.class)
                .setAction(ACTION_DONE)
                .putExtra(EXTRA_ID, todo.id);
        PendingIntent donePi = PendingIntent.getBroadcast(ctx, notifId(todo.id), done,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Action doneAction = new Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.ic_check), "완료", donePi).build();

        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("📝 할 일 확인")
                .setContentText(todo.text)
                .setStyle(new Notification.BigTextStyle().bigText(todo.text))
                .setSubText(intervalLabel(todo.intervalHours))
                .setContentIntent(openPi)
                .addAction(doneAction)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();

        ctx.getSystemService(NotificationManager.class).notify(notifId(todo.id), n);
    }

    private static PendingIntent alarmIntent(Context ctx, long id) {
        Intent i = new Intent(ctx, ReminderReceiver.class)
                .setAction(ACTION_REMIND)
                .putExtra(EXTRA_ID, id);
        return PendingIntent.getBroadcast(ctx, notifId(id), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int notifId(long id) {
        return (int) (id % Integer.MAX_VALUE);
    }
}
