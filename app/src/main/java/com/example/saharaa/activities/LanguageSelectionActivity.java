package com.example.saharaa.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.saharaa.R;

import java.util.ArrayList;
import java.util.Locale;

public class LanguageSelectionActivity extends AppCompatActivity {

    private static final int SPEECH_REQUEST = 202;
    private TextToSpeech tts;

    private boolean waitingForInput = false;
    private String selectedLanguage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_selection);

        // Back → Accessibility page
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                startActivity(new Intent(
                        LanguageSelectionActivity.this,
                        AccessibilityModeActivity.class
                ));
                finish();
            }
        });

        // Buttons for non-blind users
        findViewById(R.id.btnEnglish).setOnClickListener(v -> handleLanguage("en"));
        findViewById(R.id.btnHindi).setOnClickListener(v -> handleLanguage("hi"));
        findViewById(R.id.btnGujarati).setOnClickListener(v -> handleLanguage("gu"));

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {

                tts.setLanguage(Locale.ENGLISH);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}

                    @Override
                    public void onDone(String id) {
                        runOnUiThread(() -> {
                            switch (id) {
                                case "ASK_LANG":
                                case "RETRY_LANG":
                                    if (waitingForInput) {
                                        startListening();
                                    }
                                    break;

                                case "CONFIRM_LANG":
                                    goToLogin();
                                    break;
                            }
                        });
                    }

                    @Override public void onError(String id) {}
                });

                askLanguage();
            }
        });
    }

    // Ask user for language
    private void askLanguage() {
        waitingForInput = true;
        speak(
                "Please select your language. Say English, Hindi, or Gujarati.",
                "ASK_LANG"
        );
    }

    // Start STT
    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
        );
        startActivityForResult(intent, SPEECH_REQUEST);
    }

    // Handle STT result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (results == null || results.isEmpty()) {
                retry();
                return;
            }

            String spoken = results.get(0).toLowerCase();

            if (spoken.contains("english")) {
                handleLanguage("en");
            } else if (spoken.contains("hindi")) {
                handleLanguage("hi");
            } else if (spoken.contains("gujarati") || spoken.contains("gujrati")) {
                handleLanguage("gu");
            } else {
                retry();
            }
        }
    }

    // Handle both button & voice selection
    private void handleLanguage(String lang) {
        waitingForInput = false;
        selectedLanguage = lang;
        saveLanguage(lang);

        String msg;
        Locale locale;

        switch (lang) {
            case "hi":
                msg = "हिंदी चुनी गई है";
                locale = new Locale("hi", "IN");
                break;

            case "gu":
                msg = "ગુજરાતી પસંદ કરવામાં આવી છે";
                locale = new Locale("gu", "IN");
                break;

            default:
                msg = "English selected";
                locale = Locale.ENGLISH;
        }

        tts.setLanguage(locale);
        speak(msg, "CONFIRM_LANG");
    }

    // Retry automatically
    private void retry() {
        speak(
                "Sorry, I did not understand. Please say English, Hindi, or Gujarati.",
                "RETRY_LANG"
        );
    }

    private void saveLanguage(String lang) {
        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        prefs.edit().putString("LANGUAGE", lang).apply();
    }

    private void goToLogin() {
        startActivity(new Intent(
                LanguageSelectionActivity.this,
                LoginRegisterActivity.class
        ));
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
