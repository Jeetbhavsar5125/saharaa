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
 *
 * Subclasses MUST implement:
 *   - {@link #getWelcomeMessage()} — spoken when the screen opens (blind mode only)
 *   - {@link #processCommand(String)} — handle recognized voice commands
 *
 * Subclasses MAY override:
 *   - {@link #onTtsReady()} — called once TTS initialises (on main thread), before welcome is spoken
 *   - {@link #getLoopBackIds()} — TTS utterance IDs after which STT should auto-restart
 */
public abstract class BaseVoiceActivity extends AppCompatActivity {

    // ─── Voice fields (shared across all activities) ───────────────────────────
    protected TextToSpeech     tts;
    protected SpeechRecognizer speechRecognizer;
    protected Intent           speechIntent;

    protected boolean isBlindUser    = false;
    protected boolean isListening    = false;
    protected boolean isActivityActive = false;
    protected boolean ttsReady       = false;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Abstract interface ────────────────────────────────────────────────────

    /** Returns the TTS message spoken when the screen opens (blind mode). */
    protected abstract String getWelcomeMessage();

    /** Handles a recognised voice command. */
    protected abstract void processCommand(String command);

    /**
     * Returns the set of TTS utterance IDs after which STT should automatically restart.
     * Defaults to: WELCOME, LOOP_RETRY, MANUAL.
     */
    protected String[] getLoopBackIds() {
        return new String[]{"WELCOME", "LOOP_RETRY", "MANUAL"};
    }

    /** Called on the main thread once TTS is initialised, before welcome is spoken. */
    protected void onTtsReady() {}

    /** Called on the main thread whenever STT listening state changes (for UI status banners). */
    protected void onListeningStateChanged(boolean isListening) {}

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

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
        if (speechRecognizer == null) initSpeechRecognizer();
        if (isBlindUser && ttsReady && !isListening) {
            mainHandler.postDelayed(this::startListeningNow, 300L);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        if (tts != null) tts.stop();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            isListening = false;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.shutdown(); tts = null; }
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
    }

    // ─── TTS init ──────────────────────────────────────────────────────────────

    private void initTts(String lang) {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            Locale locale = LocaleUtils.resolve(lang);
            int result = tts.setLanguage(locale);
            float rate = getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE)
                    .getFloat(AppPrefs.KEY_SPEECH_RATE, 0.9f);
            tts.setSpeechRate(rate);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
            ttsReady = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    if (isBlindUser && isActivityActive && isLoopBackId(id)) {
                        mainHandler.post(BaseVoiceActivity.this::startListeningNow);
                    }
                }
                @Override public void onError(String id) {}
            });

            runOnUiThread(() -> {
                onTtsReady();
                if (isBlindUser) {
                    speak(getWelcomeMessage(), "WELCOME");
                }
            });
        });
    }

    private boolean isLoopBackId(String id) {
        for (String loopId : getLoopBackIds()) {
            if (loopId.equals(id)) return true;
        }
        return false;
    }

    // ─── STT init ──────────────────────────────────────────────────────────────

    protected void initSpeechRecognizer() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
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
                if (!isActivityActive || !isBlindUser) return;
                long delay = (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 0L : 300L;
                mainHandler.postDelayed(BaseVoiceActivity.this::startListeningNow, delay);
            }

            @Override
            public void onResults(Bundle results) {
                setListeningState(false);
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processCommand(matches.get(0).toLowerCase());
                } else if (isActivityActive && isBlindUser) {
                    startListeningNow();
                }
            }
        });
    }

    private void buildSpeechIntent() {
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L);
    }

    // ─── Public helpers ────────────────────────────────────────────────────────

    private void setListeningState(boolean listening) {
        this.isListening = listening;
        runOnUiThread(() -> onListeningStateChanged(listening));
    }

    /** Set speech rate dynamically (e.g. 0.75f = slow, 1.0f = normal, 1.25f = fast). */
    protected void setSpeechRate(float rate) {
        getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE)
                .edit().putFloat(AppPrefs.KEY_SPEECH_RATE, rate).apply();
        if (tts != null && ttsReady) {
            tts.setSpeechRate(rate);
        }
    }

    /** Speak text. Safe to call at any time — no-ops if TTS not ready. */
    protected void speak(String text, String id) {
        if (tts == null || !ttsReady) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    /** Cancel any stale STT session and start listening immediately. */
    protected void startListeningNow() {
        if (!isActivityActive || speechRecognizer == null || speechIntent == null) return;
        if (isListening) { speechRecognizer.cancel(); isListening = false; }
        runOnUiThread(() -> speechRecognizer.startListening(speechIntent));
    }
}
