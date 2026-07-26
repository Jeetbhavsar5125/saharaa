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
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.saharaa.R;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isBlindUser      = false;
    private boolean isListening      = false;
    private boolean isActivityActive = false;
    private boolean ttsReady         = false;
    private boolean wasEverPaused    = false; // tracks returns from child activities

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // UI
    private View btnScanObject, btnScanBarcode, btnHistory;
    private FloatingActionButton fabMic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        isActivityActive = true;

        // Init UI
        btnScanObject  = findViewById(R.id.btnScanObject);
        btnScanBarcode = findViewById(R.id.btnScanBarcode);
        btnHistory     = findViewById(R.id.btnHistory);
        fabMic         = findViewById(R.id.fabMic);

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Permissions
        if (!checkPermissions()) {
            requestPermissions();
        } else {
            initApp();
        }

        // Back Handler
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishAffinity(); }
        });

        // Click Listeners
        btnScanObject.setOnClickListener(v  -> openObjectScanner());
        btnScanBarcode.setOnClickListener(v -> openBarcodeScanner());
        btnHistory.setOnClickListener(v     -> openHistory());
        fabMic.setOnClickListener(v -> {
            if (ttsReady) speak("Listening", "MANUAL");
            startListeningNow();
        });

        View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));
        }
    }

    private void initApp() {
        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        isBlindUser = prefs.getBoolean("IS_BLIND", false);
        String lang  = prefs.getString("LANGUAGE", "en");

        // Build speech intent once
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        // ── Key fix: shorten silence detection so STT returns faster ──
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L);

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;

            Locale locale = resolveLocale(lang);
            int result = tts.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
            ttsReady = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}

                @Override
                public void onDone(String id) {
                    // Only re-trigger STT for these IDs; NAV navigates so don't loop
                    if (isBlindUser && isActivityActive
                            && ("WELCOME".equals(id) || "LOOP_RETRY".equals(id) || "MANUAL".equals(id))) {
                        mainHandler.post(() -> startListeningNow());
                    }
                }

                @Override public void onError(String id) {}
            });

            if (isBlindUser) {
                // Init SR first, then speak so listener is ready
                initSpeechRecognizer();
                speak("Saharaa Vision. Say: scan product, barcode, history, or settings.", "WELCOME");
            }
        });

        // For sighted users we still need SR initialised if FAB is tapped
        if (!isBlindUser) {
            initSpeechRecognizer();
        }
    }

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { isListening = true; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float r) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}
            @Override public void onEndOfSpeech() { isListening = false; }

            @Override
            public void onError(int error) {
                isListening = false;
                if (!isActivityActive || !isBlindUser) return;
                // Restart immediately — no delay for ERROR_NO_MATCH/TIMEOUT, tiny delay for others
                long delay = (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 0L : 300L;
                mainHandler.postDelayed(() -> startListeningNow(), delay);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
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

    /** Safe start — cancel any stale session first, then start fresh */
    private void startListeningNow() {
        if (!isActivityActive || speechRecognizer == null) return;
        if (isListening) {
            speechRecognizer.cancel();
            isListening = false;
        }
        runOnUiThread(() -> speechRecognizer.startListening(speechIntent));
    }

    private void processCommand(String command) {
        if (command.contains("object") || command.contains("product") || command.contains("item")
                || (command.contains("scan") && !command.contains("barcode"))) {
            speak("Opening scanner.", "NAV");
            openObjectScanner();
        } else if (command.contains("barcode") || command.contains("code")) {
            speak("Opening barcode scanner.", "NAV");
            openBarcodeScanner();
        } else if (command.contains("history") || command.contains("recent")) {
            speak("Opening history.", "NAV");
            openHistory();
        } else if (command.contains("setting") || command.contains("config") || command.contains("option")) {
            speak("Opening settings.", "NAV");
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            speak("Say: scan product, barcode, history, or settings.", "LOOP_RETRY");
        }
    }

    private void openObjectScanner() {
        Intent i = new Intent(this, SmartScannerActivity.class);
        i.putExtra("MODE", "OBJECT");
        startActivity(i);
    }

    private void openBarcodeScanner() {
        Intent i = new Intent(this, SmartScannerActivity.class);
        i.putExtra("MODE", "BARCODE");
        startActivity(i);
    }

    private void openHistory() {
        startActivity(new Intent(this, HistoryActivity.class));
    }

    // ─── TTS ────────────────────────────────────────────────────────────────────

    private void speak(String msg, String id) {
        if (tts == null || !ttsReady) return;
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, id);
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

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initApp();
            } else {
                Toast.makeText(this, "Permissions Required for Saharaa Vision", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        // Re-init SR (may have been destroyed on pause)
        if (speechRecognizer == null) initSpeechRecognizer();

        if (isBlindUser && ttsReady) {
            if (wasEverPaused) {
                // Returning from scanner/history/settings — re-announce menu then listen
                speak("Saharaa. Say: scan product, barcode, history, or settings.", "WELCOME");
            } else if (!isListening) {
                // First-time resume after TTS init (WELCOME already spoken in initApp)
                mainHandler.postDelayed(() -> startListeningNow(), 300L);
            }
        }
        wasEverPaused = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasEverPaused = true;
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
}
