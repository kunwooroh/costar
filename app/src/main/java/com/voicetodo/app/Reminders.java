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

    private Reminders() {}

    public static void createChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "할 일 알림", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("등록한 할 일을 정해진 간격마다 알려줍니다");
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /**
     * 첫 알림 시각 계산.
     * startAt == 0 이면 지금부터 간격 뒤, 아니면 startAt (이미 지났으면 간격만큼 건너뛴 다음 시각).
     */
    public static long firstTrigger(Interval iv, long startAt, long now) {
        if (startAt <= 0) {
            long t = iv.addTo(now);
            // 초 단위 간격이 아니면 보기 좋게 '분' 단위로 맞춤 (오후 3:30:12 → 오후 3:30)
            if (!Interval.SEC.equals(iv.unit)) t -= t % 60_000L;
            return t;
        }
        return alignedNext(iv, startAt, now);
    }

    /** t 부터 간격씩 더해 지금 이후가 되는 첫 시각 (매일 오전 9시 같은 리듬 유지). */
    public static long alignedNext(Interval iv, long t, long now) {
        if (t > now) return t;
        if (Interval.SEC.equals(iv.unit) || Interval.MIN.equals(iv.unit) || Interval.HOUR.equals(iv.unit)) {
            long step = iv.approxSeconds() * 1000L;   // 고정 길이 간격은 바로 계산
            long steps = (now - t) / step + 1;
            return t + steps * step;
        }
        long n = t;
        int guard = 0;
        while (n <= now && guard++ < 200_000) n = iv.addTo(n);
        return n > now ? n : iv.addTo(now);
    }

    /** 새로 등록하거나 간격을 바꿨을 때 첫 알림 예약. 예약된 시각을 돌려줌. */
    public static long scheduleFirst(Context ctx, Todo todo, long startAt) {
        long at = firstTrigger(todo.interval, startAt, System.currentTimeMillis());
        scheduleAt(ctx, todo.id, at);
        return at;
    }

    /** 알림이 울린 뒤 다음 알림 예약 (울린 시각 기준으로 리듬 유지). */
    public static void scheduleAfterFire(Context ctx, Todo todo) {
        long now = System.currentTimeMillis();
        long base = todo.nextAt > 0 ? todo.nextAt : now;
        scheduleAt(ctx, todo.id, alignedNext(todo.interval, todo.interval.addTo(base), now));
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
            } else if (t.nextAt > 0) {
                // 꺼져 있는 동안 놓친 알림은 지금 알려주고, 원래 리듬대로 다음 알림 예약
                showNotification(ctx, t);
                scheduleAt(ctx, t.id, alignedNext(t.interval, t.nextAt, now));
            } else {
                scheduleAt(ctx, t.id, t.interval.addTo(now));
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
                .setSubText(todo.interval.label())
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
