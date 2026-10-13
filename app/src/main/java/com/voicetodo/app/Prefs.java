package com.voicetodo.app;

import android.content.Context;
import android.content.SharedPreferences;

/** 새 할 일에 쓸 기본 알림 간격과 첫 알림 기준 시각. */
public final class Prefs {

    private static final String FILE = "settings";
    private static final String K_AMOUNT = "default_amount";
    private static final String K_UNIT = "default_unit";
    private static final String K_START = "default_start";
    private static final String K_ONCE = "default_once";

    private Prefs() {}

    public static Interval defaultInterval(Context ctx) {
        SharedPreferences p = sp(ctx);
        return Interval.of(p.getInt(K_AMOUNT, 6), p.getString(K_UNIT, Interval.HOUR));
    }

    /** 0 이면 "등록 시점부터" */
    public static long defaultStart(Context ctx) {
        return sp(ctx).getLong(K_START, 0L);
    }

    public static boolean defaultOnce(Context ctx) {
        return sp(ctx).getBoolean(K_ONCE, false);
    }

    public static void setDefault(Context ctx, Interval interval, long startAt, boolean once) {
        sp(ctx).edit()
                .putBoolean(K_ONCE, once)
                .putInt(K_AMOUNT, interval.amount)
                .putString(K_UNIT, interval.unit)
                .putLong(K_START, startAt)
                .apply();
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
