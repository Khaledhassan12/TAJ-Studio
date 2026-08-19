package pro.sketchware.ai.ui;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;

public final class VoiceReader implements TextToSpeech.OnInitListener {

    private static final String TAG = "VoiceReader";
    private TextToSpeech tts;
    private boolean ready = false;
    private final OnStateListener listener;
    private String currentUtteranceId;

    public interface OnStateListener {
        void onStart();
        void onDone();
        void onError(String message);
    }

    public VoiceReader(Context context, OnStateListener listener) {
        this.listener = listener;
        this.tts = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
                if (listener != null) listener.onError("Language not supported");
            } else {
                ready = true;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        if (listener != null) listener.onStart();
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if (listener != null) listener.onDone();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        if (listener != null) listener.onDone();
                    }
                });
            }
        } else {
            Log.e(TAG, "TTS Initialization failed");
            if (listener != null) listener.onError("No text-to-speech engine on device");
        }
    }

    public void speak(String text) {
        if (!ready) {
            if (listener != null) listener.onError("Voice engine not ready");
            return;
        }
        stop();
        currentUtteranceId = String.valueOf(System.currentTimeMillis());
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, currentUtteranceId);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
    }

    public void stop() {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
            if (listener != null) listener.onDone();
        }
    }

    public boolean isSpeaking() {
        return tts != null && tts.isSpeaking();
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
