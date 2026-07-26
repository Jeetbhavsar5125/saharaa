package com.example.saharaa.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.saharaa.R;

import java.util.ArrayList;
import java.util.Locale;

public class AccessibilityModeActivity extends AppCompatActivity {

    private TextToSpeech     tts;
    private SpeechRecognizer speechRecognizer;
    private Intent           speechIntent;

    private boolean ttsReady         = false;
    private boolean isListening      = false;
    private boolean isActivityActive = false;
    private boolean waitingForInput  = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accessibility_mode);
        isActivityActive = true;

        // Back → exit app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishAffinity(); }
        });

        // Card buttons (for sighted users)
        findViewById(R.id.cardBlindMode).setOnClickListener(v  -> handleSelection(true));
        findViewById(R.id.cardVisualMode).setOnClickListener(v -> handleSelection(false));

        // Build speech intent with fast silence detection
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L);

        // Initialize SpeechRecognizer inline (no dialog popup)
        initSpeechRecognizer();

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            tts.setLanguage(Locale.US);
            tts.setSpeechRate(0.85f);
            ttsReady = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    if (!isActivityActive) return;
                    if (("ASK".equals(id) || "RETRY".equals(id)) && waitingForInput) {
                        mainHandler.post(() -> startListeningNow());
                    }
                    if ("CONFIRM".equals(id)) {
                        mainHandler.post(() -> navigateNext());
                    }
                }
                @Override public void onError(String id) {}
            });

            askUser();
        });
    }

    // ─── STT ───────────────────────────────────────────────────────────────────

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { isListening = true; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { isListening = false; }
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}

            @Override
            public void onError(int error) {
                isListening = false;
                if (!isActivityActive || !waitingForInput) return;
                mainHandler.postDelayed(AccessibilityModeActivity.this::startListeningNow, 1200L);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processVoiceResult(matches.get(0).toLowerCase());
                } else if (isActivityActive && waitingForInput) {
                    mainHandler.postDelayed(AccessibilityModeActivity.this::startListeningNow, 800L);
                }
            }
        });
    }

    private void startListeningNow() {
        if (!isActivityActive || speechRecognizer == null || speechIntent == null) return;
        if (tts != null && tts.isSpeaking()) return; // Don't listen while speaking
        if (isListening) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
            isListening = false;
        }
        runOnUiThread(() -> {
            try {
                speechRecognizer.startListening(speechIntent);
            } catch (Exception ignored) {}
        });
    }

    private void processVoiceResult(String text) {
        if (text.contains("blind") && !text.contains("not")) {
            handleSelection(true);
        } else if (text.contains("not") || text.contains("visual") || text.contains("normal")) {
            handleSelection(false);
        } else {
            retry();
        }
    }

    // ─── Ask / Handle / Retry ──────────────────────────────────────────────────

    private void askUser() {
        waitingForInput = true;
        speak("Are you visually impaired? Please say blind, or not blind.", "ASK");
    }

    private void handleSelection(boolean isBlind) {
        waitingForInput = false;
        saveAccessibilityMode(isBlind);
        String msg = isBlind ? "Blind mode selected." : "Normal mode selected.";
        speak(msg, "CONFIRM");
    }

    private void retry() {
        speak("Sorry, I did not understand. Please say blind or not blind.", "RETRY");
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void saveAccessibilityMode(boolean isBlind) {
        getSharedPreferences("SaharaaPrefs", MODE_PRIVATE)
                .edit().putBoolean("IS_BLIND", isBlind).apply();
    }

    private void navigateNext() {
        startActivity(new Intent(this, LanguageSelectionActivity.class));
        finish();
    }

    private void speak(String text, String id) {
        if (tts == null || !ttsReady) return;
        mainHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null && isListening) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
            isListening = false;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (speechRecognizer == null) initSpeechRecognizer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        waitingForInput = false;
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
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
    }
}
