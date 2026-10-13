package com.voicetodo.app;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** "오늘 오후 3:30", "내일 오전 9:00", "10월 16일 (목) 오전 9:00" 형식. */
public final class TimeFmt {

    private TimeFmt() {}

    public static String format(long millis) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        String time = new SimpleDateFormat(c.get(Calendar.SECOND) != 0 ? "a h:mm:ss" : "a h:mm", Locale.KOREA)
                .format(new Date(millis));
        int dayDiff = dayIndex(c) - dayIndex(now);
        if (dayDiff == 0) return "오늘 " + time;
        if (dayDiff == 1) return "내일 " + time;
        if (dayDiff == 2) return "모레 " + time;
        String pattern = c.get(Calendar.YEAR) == now.get(Calendar.YEAR) ? "M월 d일 (E)" : "yyyy년 M월 d일 (E)";
        return new SimpleDateFormat(pattern, Locale.KOREA).format(new Date(millis)) + " " + time;
    }

    private static int dayIndex(Calendar c) {
        return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR);
    }
}
