package com.voicetodo.app;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 음성 문장에서 알림 간격·시작 시각을 뽑아내고 나머지를 할 일 내용으로 정리.
 * 예) "30초마다 물 확인"                → 30초마다, "물 확인"
 *     "30분마다 스트레칭"                → 30분마다, "스트레칭"
 *     "보고서 쓰기 여섯 시간마다 알려줘"   → 6시간마다, "보고서 쓰기"
 *     "매일 아침 9시에 영양제 먹기"        → 매일, 첫 알림 오전 9:00, "영양제 먹기"
 *     "두 달마다 정수기 필터 교체"         → 2개월마다
 *     "내일 오후 3시 은행 가기"            → 한 번만, 내일 오후 3:00
 *     "하루 있다가 알려줘"                 → 한 번만, 지금부터 24시간 뒤
 *     "30분 뒤에 빨래 꺼내기"              → 한 번만, 30분 뒤
 *     "1시간 뒤부터 30분마다 물 마시기"     → 30분마다, 첫 알림 1시간 뒤
 *
 * 규칙: '~마다'가 있으면 반복, '~뒤/후/있다가'나 시각만 있으면 한 번만 알림.
 */
public final class VoiceParser {

    public static final class Result {
        public final String text;
        /** 문장에 간격이 없으면 null */
        public final Interval interval;
        /** 문장에 시각·지연이 없으면 0 */
        public final long startAt;
        /** "30분 뒤" 같은 지연 길이 (없으면 null) */
        public final Interval delay;

        Result(String text, Interval interval, long startAt, Interval delay) {
            this.text = text;
            this.interval = interval;
            this.startAt = startAt;
            this.delay = delay;
        }

        /** 반복 없이 한 번만 알릴지 */
        public boolean once() {
            return interval == null && startAt > 0;
        }
    }

    private static final String NUM =
            "(\\d{1,3}|열한|열두|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|스무|이십|삼십|사십|오십|육십|구십|십오)";
    private static final String EVERY =
            "\\s*(마다|간격으로|간격|에\\s*한\\s*번씩?|에\\s*한번씩?|씩)";

    /** "30분마다", "3시간마다", "2일마다", "2주일마다", "3개월마다", "두 달에 한 번" */
    private static final Pattern NUM_INTERVAL = Pattern.compile(
            "(?<![0-9])" + NUM + "\\s*(초|분|시간|일|주일|주|개월|달)" + EVERY);
    private static final Pattern DAILY = Pattern.compile("매일|날마다|하루\\s*(마다|에\\s*한\\s*번씩?|에\\s*한번씩?|한\\s*번씩?)");
    private static final Pattern WEEKLY = Pattern.compile("매주|주마다|일주일\\s*(마다|에\\s*한\\s*번씩?|에\\s*한번씩?)");
    private static final Pattern MONTHLY = Pattern.compile("매달|매월|달마다|한\\s*달\\s*(마다|에\\s*한\\s*번씩?|에\\s*한번씩?)");
    private static final Pattern HOURLY = Pattern.compile("매\\s*시간|(?<![0-9가-힣])시간마다");

    private static final String AFTER =
            "\\s*(뒤|후|있다가|있다|이따가?|지나서|지나면)\\s*(에는|에|부터)?";

    /** "30분 뒤에", "2시간 반 후", "3일 있다가", "한 달 지나서" */
    private static final Pattern DELAY_NUM = Pattern.compile(
            "(?<![0-9])" + NUM + "\\s*(초|분|시간|일|주일|주|개월|달)(\\s*반)?" + AFTER);
    /** "하루 있다가", "이틀 뒤", "일주일 후", "반나절 뒤", "조금 있다가" */
    private static final Pattern DELAY_WORD = Pattern.compile(
            "(하루|이틀|사흘|나흘|일주일|반나절|조금|잠깐|잠시)" + AFTER);
    /** 시각 없이 "내일", "모레 아침" */
    private static final Pattern DAY_ONLY = Pattern.compile(
            "(내일|모레)\\s*(아침|오전|낮|오후|저녁|밤)?\\s*(에|쯤)?");

    /** "내일 오후 3시 30분에", "아침 9시", "저녁 7시 반부터" */
    private static final Pattern TIME = Pattern.compile(
            "(내일|모레)?\\s*(오전|오후|아침|낮|저녁|밤|새벽)?\\s*"
                    + "(\\d{1,2}|열한|열두|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*시(?!간)"
                    + "(\\s*반|\\s*(\\d{1,2})\\s*분)?\\s*(에|부터)?");

    private static final Pattern TAIL = Pattern.compile(
            "\\s*(알림\\s*(줘|주세요|해\\s*줘|해\\s*주세요|설정)?|알려\\s*(줘|주세요|줄래)?|리마인드\\s*(해\\s*줘)?)\\s*[.!?]*\\s*$");

    private VoiceParser() {}

    public static Result parse(String raw) {
        return parse(raw, Calendar.getInstance());
    }

    public static Result parse(String raw, Calendar now) {
        if (raw == null) return new Result("", null, 0, null);
        String s = raw.trim();
        Interval interval = null;
        Interval delay = null;
        long startAt = 0;

        // 1) "30분 뒤", "하루 있다가" → 지연
        Matcher m = DELAY_NUM.matcher(s);
        if (m.find()) {
            int n = toNumber(m.group(1));
            String unit = toUnit(m.group(2));
            if (m.group(3) != null && Interval.HOUR.equals(unit)) {   // "2시간 반"
                delay = Interval.of(n * 60 + 30, Interval.MIN);
            } else if (m.group(3) != null && Interval.MIN.equals(unit)) {
                delay = Interval.of(n * 60 + 30, Interval.SEC);
            } else {
                delay = Interval.of(n, unit);
            }
            s = remove(s, m);
        } else if ((m = DELAY_WORD.matcher(s)).find()) {
            delay = wordDelay(m.group(1));
            s = remove(s, m);
        }
        if (delay != null) {
            long at = delay.addTo(now.getTimeInMillis());
            if (!Interval.SEC.equals(delay.unit)) at -= at % 60_000L;   // 분 단위로 정리
            else at -= at % 1000L;
            startAt = at;
        }

        // 2) "30분마다", "매일" → 반복 간격
        m = NUM_INTERVAL.matcher(s);
        if (m.find()) {
            interval = Interval.of(toNumber(m.group(1)), toUnit(m.group(2)));
            s = remove(s, m);
        } else if ((m = DAILY.matcher(s)).find()) {
            interval = Interval.of(1, Interval.DAY);
            s = remove(s, m);
        } else if ((m = WEEKLY.matcher(s)).find()) {
            interval = Interval.of(1, Interval.WEEK);
            s = remove(s, m);
        } else if ((m = MONTHLY.matcher(s)).find()) {
            interval = Interval.of(1, Interval.MONTH);
            s = remove(s, m);
        } else if ((m = HOURLY.matcher(s)).find()) {
            interval = Interval.of(1, Interval.HOUR);
            s = remove(s, m);
        }

        // 3) "아침 9시", "내일 오후 3시" → 시각 (지연이 없을 때만)
        if (startAt == 0) {
            Matcher t = TIME.matcher(s);
            if (t.find()) {
                startAt = toStart(t, now);
                s = remove(s, t);
            } else if ((t = DAY_ONLY.matcher(s)).find()) {
                startAt = dayOnly(t, now);
                s = remove(s, t);
            }
        }

        if (interval != null || startAt > 0) {
            s = TAIL.matcher(s).replaceAll("");
            s = s.replaceAll("^[\\s,]*(에|로|으로|부터)?[\\s,]+", "").replaceAll("[\\s,]+$", "");
        }
        s = s.replaceAll("\\s{2,}", " ").trim();
        if (s.isEmpty()) s = (interval != null || startAt > 0) ? "알림" : raw.trim();
        return new Result(s, interval, startAt, delay);
    }

    private static Interval wordDelay(String w) {
        switch (w) {
            case "하루": return Interval.of(1, Interval.DAY);
            case "이틀": return Interval.of(2, Interval.DAY);
            case "사흘": return Interval.of(3, Interval.DAY);
            case "나흘": return Interval.of(4, Interval.DAY);
            case "일주일": return Interval.of(1, Interval.WEEK);
            case "반나절": return Interval.of(6, Interval.HOUR);
            default: return Interval.of(10, Interval.MIN);   // 조금/잠깐/잠시
        }
    }

    /** "내일" → 내일 오전 9시, "내일 저녁" → 내일 저녁 7시 */
    private static long dayOnly(Matcher t, Calendar now) {
        int offset = "모레".equals(t.group(1)) ? 2 : 1;
        String period = t.group(2);
        int hour = 9;
        if (period != null) {
            switch (period) {
                case "낮": hour = 12; break;
                case "오후": hour = 15; break;
                case "저녁": hour = 19; break;
                case "밤": hour = 21; break;
                default: hour = 9; break;
            }
        }
        Calendar c = (Calendar) now.clone();
        c.add(Calendar.DAY_OF_MONTH, offset);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long toStart(Matcher t, Calendar now) {
        String day = t.group(1);
        String period = t.group(2);
        int hour = toNumber(t.group(3));
        int minute = 0;
        if (t.group(4) != null) minute = t.group(5) != null ? Integer.parseInt(t.group(5)) : 30;
        if (hour > 24 || minute > 59) return 0;
        if (hour == 24) hour = 0;

        int dayOffset = "내일".equals(day) ? 1 : "모레".equals(day) ? 2 : 0;
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.MINUTE, minute);
        c.add(Calendar.DAY_OF_MONTH, dayOffset);

        if (period != null) {
            switch (period) {
                case "오후": case "저녁": case "밤":
                    if (hour < 12) hour += 12; break;
                case "낮":
                    if (hour < 7) hour += 12; break;
                case "오전": case "아침": case "새벽":
                    if (hour == 12) hour = 0; break;
                default: break;
            }
            c.set(Calendar.HOUR_OF_DAY, hour);
            if (dayOffset == 0 && c.getTimeInMillis() <= now.getTimeInMillis()) {
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
            return c.getTimeInMillis();
        }

        // 오전/오후를 말하지 않은 경우
        if (dayOffset > 0 || hour >= 12) {
            if (dayOffset > 0 && hour < 7) hour += 12;
            c.set(Calendar.HOUR_OF_DAY, hour);
            if (c.getTimeInMillis() <= now.getTimeInMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
            return c.getTimeInMillis();
        }
        // 오늘 h시와 (h+12)시 중 가장 가까운 미래
        c.set(Calendar.HOUR_OF_DAY, hour);
        if (c.getTimeInMillis() > now.getTimeInMillis()) return c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, hour + 12);
        if (c.getTimeInMillis() > now.getTimeInMillis()) return c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    private static String remove(String s, Matcher m) {
        return (s.substring(0, m.start()) + " " + s.substring(m.end())).trim();
    }

    private static String toUnit(String u) {
        switch (u) {
            case "초": return Interval.SEC;
            case "분": return Interval.MIN;
            case "일": return Interval.DAY;
            case "주": case "주일": return Interval.WEEK;
            case "개월": case "달": return Interval.MONTH;
            default: return Interval.HOUR;
        }
    }

    static int toNumber(String w) {
        if (w.matches("\\d+")) return Integer.parseInt(w);
        switch (w) {
            case "한": return 1;
            case "두": return 2;
            case "세": return 3;
            case "네": return 4;
            case "다섯": return 5;
            case "여섯": return 6;
            case "일곱": return 7;
            case "여덟": return 8;
            case "아홉": return 9;
            case "열": return 10;
            case "열한": return 11;
            case "열두": return 12;
            case "십오": return 15;
            case "스무": case "이십": return 20;
            case "삼십": return 30;
            case "사십": return 40;
            case "오십": return 50;
            case "육십": return 60;
            case "구십": return 90;
            default: return 1;
        }
    }
}
