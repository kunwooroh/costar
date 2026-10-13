package com.voicetodo.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 음성 문장에서 알림 간격을 뽑아내고 나머지를 할 일 내용으로 정리.
 * 예) "3시간마다 물 마시기"        → 3, "물 마시기"
 *     "보고서 쓰기 여섯 시간마다 알려줘" → 6, "보고서 쓰기"
 *     "매일 영양제 먹기"            → 24, "영양제 먹기"
 */
public final class VoiceParser {

    public static final class Result {
        public final String text;
        /** 문장에서 간격을 못 찾으면 0 */
        public final int intervalHours;

        Result(String text, int intervalHours) {
            this.text = text;
            this.intervalHours = intervalHours;
        }
    }

    private static final Pattern HOURS = Pattern.compile(
            "(?<![0-9])(3|세|6|여섯|9|아홉|24|스물\\s?네)\\s*시간\\s*(마다|간격으로|간격|에\\s*한\\s*번씩?|에\\s*한번씩?|씩)?");
    private static final Pattern DAILY = Pattern.compile(
            "하루\\s*(마다|에\\s*한\\s*번씩?|에\\s*한번씩?|한\\s*번씩?|한번씩?)|매일|날마다");
    private static final Pattern TAIL = Pattern.compile(
            "\\s*(알림\\s*(줘|주세요|해\\s*줘|해\\s*주세요|설정)?|알려\\s*(줘|주세요|줄래)?|리마인드\\s*(해\\s*줘)?)\\s*[.!?]*\\s*$");

    private VoiceParser() {}

    public static Result parse(String raw) {
        if (raw == null) return new Result("", 0);
        String s = raw.trim();
        int hours = 0;

        Matcher m = HOURS.matcher(s);
        if (m.find()) {
            hours = toHours(m.group(1));
            s = remove(s, m);
        } else {
            Matcher d = DAILY.matcher(s);
            if (d.find()) {
                hours = 24;
                s = remove(s, d);
            }
        }

        if (hours > 0) {
            s = TAIL.matcher(s).replaceAll("");
            s = s.replaceAll("^[\\s,]*(에|로|으로)?[\\s,]+", "").replaceAll("[\\s,]+$", "");
        }
        s = s.replaceAll("\\s{2,}", " ").trim();
        if (s.isEmpty()) s = raw.trim();
        return new Result(s, hours);
    }

    private static String remove(String s, Matcher m) {
        return (s.substring(0, m.start()) + " " + s.substring(m.end())).trim();
    }

    private static int toHours(String word) {
        String w = word.replace(" ", "");
        switch (w) {
            case "3": case "세": return 3;
            case "6": case "여섯": return 6;
            case "9": case "아홉": return 9;
            default: return 24;
        }
    }
}
