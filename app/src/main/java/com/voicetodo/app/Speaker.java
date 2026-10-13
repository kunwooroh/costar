package com.voicetodo.app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** 할 일을 소리내어 읽어주는 TTS 도우미 + 음성 알림 설정. */
public final class Speaker {

    private static final String PREFS = "settings";
    private static final String KEY_TTS = "tts_enabled";
    /** 알람 수신 처리 시간 제한 안에 끝내기 위한 최대 재생 시간 */
    private static final long MAX_MILLIS = 9_000L;

    private Speaker() {}

    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_TTS, true);
    }

    public static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_TTS, on).apply();
    }

    /** 무음·진동 모드이거나 방해 금지 모드면 읽지 않음. */
    public static boolean canSpeakNow(Context ctx) {
        AudioManager am = ctx.getSystemService(AudioManager.class);
        if (am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return false;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        return nm == null
                || nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL
                || nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    /**
     * 문장을 한국어로 읽음. 다 읽거나 실패·시간 초과 시 onFinish 를 한 번 호출.
     * 결과 콜백은 메인 스레드에서 실행됨.
     */
    public static void speak(Context ctx, String text, Runnable onFinish) {
        Context app = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        TextToSpeech[] holder = new TextToSpeech[1];
        AtomicBoolean finished = new AtomicBoolean(false);

        Runnable finish = () -> {
            if (!finished.compareAndSet(false, true)) return;
            TextToSpeech tts = holder[0];
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
            if (onFinish != null) onFinish.run();
        };
        main.postDelayed(finish, MAX_MILLIS);

        holder[0] = new TextToSpeech(app, status -> main.post(() -> {
            TextToSpeech tts = holder[0];
            if (finished.get() || tts == null) return;
            if (status != TextToSpeech.SUCCESS) {
                finish.run();
                return;
            }
            int lang = tts.setLanguage(Locale.KOREAN);
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                finish.run();
                return;
            }
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { main.post(finish); }
                @Override public void onError(String id) { main.post(finish); }
            });
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "todo");
        }));
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
