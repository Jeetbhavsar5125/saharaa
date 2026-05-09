package com.example.saharaa.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.saharaa.R;

import java.util.ArrayList;
import java.util.Locale;

public class AccessibilityModeActivity extends AppCompatActivity {

    private static final int SPEECH_REQUEST_CODE = 101;
    private TextToSpeech tts;
    private boolean waitingForInput = false;
    private boolean pendingNavigationBlind = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accessibility_mode);

        // Back → exit app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        // Buttons
        // Cards
        findViewById(R.id.cardBlindMode).setOnClickListener(v -> handleSelection(true));
        findViewById(R.id.cardVisualMode).setOnClickListener(v -> handleSelection(false));

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(0.85f); // 🔹 Slower speed

                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {
                        if ("ASK".equals(id) && waitingForInput) {
                            runOnUiThread(() -> startListening());
                        }
                        if ("CONFIRM".equals(id)) {
                            runOnUiThread(() -> navigateNext());
                        }
                        if ("RETRY".equals(id)) {
                            // 🔹 Auto-restart listening after error message
                            runOnUiThread(() -> startListening());
                        }

                    }

                    @Override
                    public void onError(String id) {
                    }
                });

                askUser();
            }
        });
    }

    // Ask question
    private void askUser() {
        waitingForInput = true;
        speak("Are you visually impaired? Please say blind, or not blind.", "ASK");
    }

    // Handle button OR voice selection
    private void handleSelection(boolean isBlind) {
        waitingForInput = false;
        pendingNavigationBlind = isBlind;
        saveAccessibilityMode(isBlind);

        String msg = isBlind
                ? "Blind mode selected."
                : "Normal mode selected.";

        speak(msg, "CONFIRM");
    }

    // Start STT
    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say Blind or Not Blind");

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            retry();
        }
    }

    // Handle voice result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE) {
            // Check for SUCCESS
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                if (results == null || results.isEmpty()) {
                    retry();
                    return;
                }

                String text = results.get(0).toLowerCase();

                if (text.contains("blind") && !text.contains("not")) {
                    handleSelection(true);
                } else if (text.contains("not") || text.contains("visual") || text.contains("normal")) {
                    handleSelection(false);
                } else {
                    retry();
                }
            } else {
                // 🔹 If suppressed/canceled/error, auto-retry
                retry();
            }
        }
    }

    // Retry automatically
    private void retry() {
        speak(
                "Sorry, I did not understand. Please say blind or not blind.",
                "RETRY");
    }

    // Save choice
    private void saveAccessibilityMode(boolean isBlind) {
        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("IS_BLIND", isBlind).apply();
    }

    // Navigate after confirmation speech
    private void navigateNext() {
        startActivity(new Intent(
                AccessibilityModeActivity.this,
                LanguageSelectionActivity.class));
        finish();
    }

    private void speak(String text, String id) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
