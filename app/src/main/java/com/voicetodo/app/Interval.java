package com.voicetodo.app;

import java.util.Calendar;

/** 알림 간격: 숫자 + 단위 (10초 ~ 몇 달). 달 단위는 달력 기준으로 계산. */
public final class Interval {

    public static final String SEC = "SEC";
    public static final String MIN = "MIN";
    public static final String HOUR = "HOUR";
    public static final String DAY = "DAY";
    public static final String WEEK = "WEEK";
    public static final String MONTH = "MONTH";

    /** 최소 간격 10초 */
    public static final int MIN_SECONDS = 10;

    public final int amount;
    public final String unit;

    public Interval(int amount, String unit) {
        String u = unit == null ? HOUR : unit;
        int a = Math.max(1, amount);
        if (SEC.equals(u) && a < MIN_SECONDS) a = MIN_SECONDS;
        this.amount = a;
        this.unit = u;
    }

    public static Interval of(int amount, String unit) {
        return new Interval(amount, unit);
    }

    /** 주어진 시각에 간격을 더한 시각. */
    public long addTo(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        switch (unit) {
            case SEC: c.add(Calendar.SECOND, amount); break;
            case MIN: c.add(Calendar.MINUTE, amount); break;
            case DAY: c.add(Calendar.DAY_OF_MONTH, amount); break;
            case WEEK: c.add(Calendar.DAY_OF_MONTH, amount * 7); break;
            case MONTH: c.add(Calendar.MONTH, amount); break;
            case HOUR:
            default: c.add(Calendar.HOUR_OF_DAY, amount); break;
        }
        return c.getTimeInMillis();
    }

    /** 대략적인 길이(초). 비교용. */
    public long approxSeconds() {
        switch (unit) {
            case SEC: return amount;
            case MIN: return amount * 60L;
            case DAY: return amount * 86400L;
            case WEEK: return amount * 604800L;
            case MONTH: return amount * 2592000L;
            case HOUR:
            default: return amount * 3600L;
        }
    }

    /** "30초마다", "30분마다", "6시간마다", "매일", "3일마다", "매주", "매달", "2개월마다" */
    public String label() {
        switch (unit) {
            case SEC: return amount + "초마다";
            case MIN: return amount + "분마다";
            case DAY: return amount == 1 ? "매일" : amount + "일마다";
            case WEEK: return amount == 1 ? "매주" : amount + "주마다";
            case MONTH: return amount == 1 ? "매달" : amount + "개월마다";
            case HOUR:
            default: return amount + "시간마다";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Interval)) return false;
        Interval i = (Interval) o;
        return i.amount == amount && i.unit.equals(unit);
    }

    @Override
    public int hashCode() {
        return amount * 31 + unit.hashCode();
    }
}
