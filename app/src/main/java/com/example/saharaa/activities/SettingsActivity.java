package com.example.saharaa.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.saharaa.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;

    private boolean isBlindUser      = false;
    private boolean isListening      = false;
    private boolean isActivityActive = false;
    private boolean ttsReady         = false;

    private TextView tvVoiceStatus;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int AUDIO_PERMISSION_CODE = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        isActivityActive = true;

        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        isBlindUser = prefs.getBoolean("IS_BLIND", false);
        String lang = prefs.getString("LANGUAGE", "en");

        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);

        // Back button
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Settings buttons
        findViewById(R.id.btnChangeMode).setOnClickListener(v ->
                startActivity(new Intent(this, AccessibilityModeActivity.class)));
        findViewById(R.id.btnChangeLanguage).setOnClickListener(v ->
                startActivity(new Intent(this, LanguageSelectionActivity.class)));
        findViewById(R.id.btnLoginRegister).setOnClickListener(v ->
                startActivity(new Intent(this, LoginRegisterActivity.class)));
        findViewById(R.id.btnResetApp).setOnClickListener(v -> resetApp(prefs));

        // Mic FAB
        FloatingActionButton fabMic = findViewById(R.id.fabMic);
        if (isBlindUser) {
            fabMic.setVisibility(View.VISIBLE);
            fabMic.setOnClickListener(v -> {
                if (ttsReady) speak("Listening", "MANUAL");
                startListeningNow();
            });
        }

        // Build speech intent with shorter silence windows
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L);

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(resolveLocale(lang));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                tts.setLanguage(Locale.US);
            ttsReady = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    if (!isBlindUser || !isActivityActive) return;
                    if ("WELCOME".equals(id) || "LOOP_RETRY".equals(id) || "MANUAL".equals(id)) {
                        mainHandler.post(() -> startListeningNow());
                    }
                }
                @Override public void onError(String id) {}
            });

            if (isBlindUser) {
                // Init STT first, then speak
                initSpeechRecognizer();
                speak("Settings. Say: mode, language, login, reset, or back.", "WELCOME");
            }
        });

        // Init STT for non-blind too (FAB fallback)
        if (!isBlindUser) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                initSpeechRecognizer();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
            }
        }
    }

    // ─── TTS ────────────────────────────────────────────────────────────────────

    private void speak(String text, String id) {
        if (tts == null || !ttsReady) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    // ─── STT ───────────────────────────────────────────────────────────────────

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                runOnUiThread(() -> showVoiceStatus(true));
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] buf) {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}

            @Override
            public void onEndOfSpeech() {
                isListening = false;
                runOnUiThread(() -> showVoiceStatus(false));
            }

            @Override
            public void onError(int error) {
                isListening = false;
                runOnUiThread(() -> showVoiceStatus(false));
                if (!isActivityActive || !isBlindUser) return;
                long delay = (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 0L : 300L;
                mainHandler.postDelayed(() -> startListeningNow(), delay);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                runOnUiThread(() -> showVoiceStatus(false));
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

    private void startListeningNow() {
        if (!isActivityActive || speechRecognizer == null) return;
        if (isListening) { speechRecognizer.cancel(); isListening = false; }
        runOnUiThread(() -> speechRecognizer.startListening(speechIntent));
    }

    private void processCommand(String command) {
        if (command.contains("mode") || command.contains("visual")
                || command.contains("blind") || command.contains("accessibility")) {
            speak("Opening accessibility mode.", "NAV");
            startActivity(new Intent(this, AccessibilityModeActivity.class));
        } else if (command.contains("language") || command.contains("lang")) {
            speak("Opening language.", "NAV");
            startActivity(new Intent(this, LanguageSelectionActivity.class));
        } else if (command.contains("login") || command.contains("register") || command.contains("account")) {
            speak("Opening login.", "NAV");
            startActivity(new Intent(this, LoginRegisterActivity.class));
        } else if (command.contains("reset") || command.contains("clear") || command.contains("delete")) {
            speak("Resetting app.", "NAV");
            resetApp(getSharedPreferences("SaharaaPrefs", MODE_PRIVATE));
        } else if (command.contains("back") || command.contains("exit") || command.contains("close")) {
            speak("Going back.", "NAV");
            finish();
        } else {
            speak("Say: mode, language, login, reset, or back.", "LOOP_RETRY");
        }
    }

    private void showVoiceStatus(boolean show) {
        if (tvVoiceStatus != null)
            tvVoiceStatus.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ─── Reset App ─────────────────────────────────────────────────────────────

    private void resetApp(SharedPreferences prefs) {
        prefs.edit().clear().apply();
        Toast.makeText(this, "App Reset", Toast.LENGTH_SHORT).show();
        if (isBlindUser && tts != null && ttsReady)
            tts.speak("App Reset. Restarting.", TextToSpeech.QUEUE_ADD, null, null);
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ─── Locale ────────────────────────────────────────────────────────────────

    private Locale resolveLocale(String lang) {
        switch (lang) {
            case "hi": return new Locale("hi", "IN");
            case "gu": return new Locale("gu", "IN");
            default:   return Locale.US;
        }
    }

    // ─── Permissions ───────────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer();
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (speechRecognizer == null) initSpeechRecognizer();
        if (isBlindUser && ttsReady && !isListening)
            mainHandler.postDelayed(() -> startListeningNow(), 300L);
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
        showVoiceStatus(false);
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.shutdown(); tts = null; }
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
    }
}
