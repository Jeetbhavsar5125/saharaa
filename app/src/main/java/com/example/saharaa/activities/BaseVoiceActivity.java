package com.example.saharaa.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.saharaa.utils.AppPrefs;
import com.example.saharaa.utils.LocaleUtils;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Abstract base class providing shared TTS + STT boilerplate for all voice-enabled screens.
 */
public abstract class BaseVoiceActivity extends AppCompatActivity {

    protected TextToSpeech     tts;
    protected SpeechRecognizer speechRecognizer;
    protected Intent           speechIntent;

    protected boolean isBlindUser    = false;
    protected boolean isListening    = false;
    protected boolean isActivityActive = false;
    protected boolean ttsReady       = false;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper());

    protected int consecutiveErrors = 0;

    protected abstract String getWelcomeMessage();
    protected abstract void processCommand(String command);

    protected String[] getLoopBackIds() {
        return new String[]{"WELCOME", "LOOP_RETRY", "MANUAL", "READY", "RESULT_PROMPT", "SAVED"};
    }

    protected void onTtsReady() {}
    protected void onListeningStateChanged(boolean isListening) {}

    /** Returns whether auto-voice listening loop should be active. Defaults to true if audio permission granted. */
    protected boolean isVoiceLoopEnabled() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isActivityActive = true;

        SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE);
        isBlindUser = prefs.getBoolean(AppPrefs.KEY_IS_BLIND, false);
        String lang = prefs.getString(AppPrefs.KEY_LANGUAGE, "en");

        buildSpeechIntent();
        initSpeechRecognizer();
        initTts(lang);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        consecutiveErrors = 0;
        if (speechRecognizer == null) initSpeechRecognizer();
        if (isVoiceLoopEnabled() && ttsReady && !isListening && (tts == null || !tts.isSpeaking())) {
            mainHandler.postDelayed(this::startListeningNow, 800L);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (tts != null) tts.stop();
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
            isListening = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }

    private void initTts(String lang) {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            Locale locale = LocaleUtils.resolve(lang);
            tts.setLanguage(locale);
            float rate = getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE)
                    .getFloat(AppPrefs.KEY_SPEECH_RATE, 0.9f);
            tts.setSpeechRate(rate);

            ttsReady = true;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    consecutiveErrors = 0;
                    if (isVoiceLoopEnabled() && isActivityActive && isLoopBackId(id)) {
                        mainHandler.postDelayed(BaseVoiceActivity.this::startListeningNow, 800L);
                    }
                }
                @Override public void onError(String id) {}
            });

            runOnUiThread(() -> {
                onTtsReady();
                speak(getWelcomeMessage(), "WELCOME");
            });
        });
    }

    private boolean isLoopBackId(String id) {
        for (String loopId : getLoopBackIds()) {
            if (loopId.equals(id)) return true;
        }
        return false;
    }

    protected void initSpeechRecognizer() {
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return;

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { setListeningState(true); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float r) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() { setListeningState(false); }
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}

            @Override
            public void onError(int error) {
                setListeningState(false);
                if (!isActivityActive || !isVoiceLoopEnabled()) return;

                consecutiveErrors++;
                if (consecutiveErrors > 3) {
                    // Stop aggressive auto-listening retry loop if failing repeatedly
                    return;
                }
                mainHandler.postDelayed(BaseVoiceActivity.this::startListeningNow, 1500L);
            }

            @Override
            public void onResults(Bundle results) {
                setListeningState(false);
                consecutiveErrors = 0;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processCommand(matches.get(0).toLowerCase());
                } else if (isActivityActive && isVoiceLoopEnabled()) {
                    mainHandler.postDelayed(BaseVoiceActivity.this::startListeningNow, 1000L);
                }
            }
        });
    }

    private void buildSpeechIntent() {
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
    }

    private void setListeningState(boolean listening) {
        this.isListening = listening;
        runOnUiThread(() -> onListeningStateChanged(listening));
    }

    protected void setSpeechRate(float rate) {
        getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE)
                .edit().putFloat(AppPrefs.KEY_SPEECH_RATE, rate).apply();
        if (tts != null && ttsReady) tts.setSpeechRate(rate);
    }

    protected void speak(String text, String id) {
        if (tts == null || !ttsReady) return;
        // Stop any active STT listening when TTS speaks to avoid microphone recording TTS output
        stopListeningImmediate();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    protected void stopListeningImmediate() {
        mainHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null && isListening) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
            setListeningState(false);
        }
    }

    protected void startListeningNow() {
        consecutiveErrors = 0;
        if (!isActivityActive || speechRecognizer == null || speechIntent == null) return;
        if (tts != null && tts.isSpeaking()) return; // Never listen while TTS is speaking!

        if (isListening) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
            isListening = false;
        }
        runOnUiThread(() -> {
            try {
                speechRecognizer.startListening(speechIntent);
            } catch (Exception e) {
                initSpeechRecognizer();
            }
        });
    }
}
