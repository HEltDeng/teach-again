package com.teachagain.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * 系统中文 TTS 封装。初始化失败不影响其它功能（静默降级）。
 */
public class TtsHelper {

    private TextToSpeech tts;
    private boolean ready = false;

    public TtsHelper(Context ctx) {
        try {
            tts = new TextToSpeech(ctx.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int r = tts.setLanguage(Locale.CHINESE);
                    ready = (r != TextToSpeech.LANG_MISSING_DATA
                            && r != TextToSpeech.LANG_NOT_SUPPORTED);
                    tts.setSpeechRate(0.9f); // 慢一点，长辈听得清
                }
            });
        } catch (Exception e) {
            ready = false;
        }
    }

    public void speak(String text) {
        if (tts == null || text == null) return;
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "step");
        } catch (Exception e) {
            // 忽略
        }
    }

    public void stop() {
        if (tts == null) return;
        try {
            tts.stop();
        } catch (Exception e) {
            // 忽略
        }
    }

    public boolean isReady() {
        return ready;
    }

    public void shutdown() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception e) {
            // 忽略
        }
        tts = null;
    }
}
