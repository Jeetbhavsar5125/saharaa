package com.example.saharaa.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.saharaa.R;
import com.example.saharaa.utils.AppPrefs;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class SettingsActivity extends BaseVoiceActivity {

    private TextView tvVoiceStatus, tvCurrentSpeed;

    // ─── BaseVoiceActivity contract ────────────────────────────────────────────

    @Override
    protected String getWelcomeMessage() {
        return "Settings. Say: mode, language, speed, login, reset, or back.";
    }

    @Override
    protected void processCommand(String command) {
        if (command.contains("mode") || command.contains("visual")
                || command.contains("blind") || command.contains("accessibility")) {
            speak("Opening accessibility mode.", "NAV");
            startActivity(new Intent(this, AccessibilityModeActivity.class));
        } else if (command.contains("language") || command.contains("lang")) {
            speak("Opening language.", "NAV");
            startActivity(new Intent(this, LanguageSelectionActivity.class));
        } else if (command.contains("speed") || command.contains("rate") || command.contains("fast") || command.contains("slow")) {
            cycleSpeechRate();
        } else if (command.contains("login") || command.contains("register") || command.contains("account")) {
            speak("Opening login.", "NAV");
            startActivity(new Intent(this, LoginRegisterActivity.class));
        } else if (command.contains("reset") || command.contains("clear") || command.contains("delete")) {
            speak("Resetting app.", "NAV");
            resetApp();
        } else if (command.contains("back") || command.contains("exit") || command.contains("close")) {
            speak("Going back.", "NAV");
            finish();
        } else {
            speak("Say: mode, language, speed, login, reset, or back.", "LOOP_RETRY");
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // BaseVoiceActivity handles TTS/STT + prefs
        setContentView(R.layout.activity_settings);

        tvVoiceStatus  = findViewById(R.id.tvVoiceStatus);
        tvCurrentSpeed = findViewById(R.id.tvCurrentSpeed);

        // Back button
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Settings buttons
        findViewById(R.id.btnChangeMode).setOnClickListener(v ->
                startActivity(new Intent(this, AccessibilityModeActivity.class)));
        findViewById(R.id.btnChangeLanguage).setOnClickListener(v ->
                startActivity(new Intent(this, LanguageSelectionActivity.class)));
        findViewById(R.id.btnChangeSpeechRate).setOnClickListener(v -> cycleSpeechRate());
        findViewById(R.id.btnLoginRegister).setOnClickListener(v ->
                startActivity(new Intent(this, LoginRegisterActivity.class)));
        findViewById(R.id.btnResetApp).setOnClickListener(v -> resetApp());

        updateSpeedText();

        // Mic FAB
        FloatingActionButton fabMic = findViewById(R.id.fabMic);
        if (isBlindUser) {
            fabMic.setVisibility(View.VISIBLE);
            fabMic.setOnClickListener(v -> {
                speak("Listening", "MANUAL");
                startListeningNow();
            });
        }
    }

    @Override
    protected void onListeningStateChanged(boolean isListening) {
        showVoiceStatus(isListening);
    }

    private void cycleSpeechRate() {
        float current = getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE)
                .getFloat(AppPrefs.KEY_SPEECH_RATE, 0.9f);
        float next;
        String name;
        if (current <= 0.75f) {
            next = 0.9f;
            name = "Normal speed";
        } else if (current <= 0.95f) {
            next = 1.2f;
            name = "Fast speed";
        } else {
            next = 0.7f;
            name = "Slow speed";
        }
        setSpeechRate(next);
        updateSpeedText();
        speak("Voice speed set to " + name, "LOOP_RETRY");
    }

    private void updateSpeedText() {
        if (tvCurrentSpeed == null) return;
        float current = getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE)
                .getFloat(AppPrefs.KEY_SPEECH_RATE, 0.9f);
        if (current <= 0.75f) {
            tvCurrentSpeed.setText("Slow (0.7x)");
        } else if (current <= 0.95f) {
            tvCurrentSpeed.setText("Normal (0.9x)");
        } else {
            tvCurrentSpeed.setText("Fast (1.2x)");
        }
    }

    // ─── Reset ─────────────────────────────────────────────────────────────────

    private void resetApp() {
        getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(this, "App Reset", Toast.LENGTH_SHORT).show();
        if (isBlindUser && tts != null && ttsReady)
            tts.speak("App Reset. Restarting.", TextToSpeech.QUEUE_ADD, null, null);
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showVoiceStatus(boolean show) {
        if (tvVoiceStatus != null)
            tvVoiceStatus.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
