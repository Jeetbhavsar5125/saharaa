package com.example.saharaa.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.saharaa.R;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private boolean isBlindUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        isBlindUser = prefs.getBoolean("IS_BLIND", false);
        String lang = prefs.getString("LANGUAGE", "en");

        // Init TTS for Blind Users
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale locale = new Locale(lang);
                if (lang.equals("hi"))
                    locale = new Locale("hi", "IN");
                if (lang.equals("gu"))
                    locale = new Locale("gu", "IN");
                tts.setLanguage(locale);

                if (isBlindUser) {
                    tts.speak("Settings Opened. Options are Change Mode, Language, Login, or Reset App.",
                            TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        });

        Button btnMode = findViewById(R.id.btnChangeMode);
        Button btnLang = findViewById(R.id.btnChangeLanguage);
        Button btnLogin = findViewById(R.id.btnLoginRegister);
        Button btnReset = findViewById(R.id.btnResetApp);

        btnMode.setOnClickListener(v -> {
            startActivity(new Intent(this, AccessibilityModeActivity.class));
        });

        btnLang.setOnClickListener(v -> {
            startActivity(new Intent(this, LanguageSelectionActivity.class));
        });

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginRegisterActivity.class));
        });

        btnReset.setOnClickListener(v -> {
            // WIPE DATA
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            Toast.makeText(this, "App Reset", Toast.LENGTH_SHORT).show();
            if (isBlindUser && tts != null) {
                tts.speak("App Reset. Restarting.", TextToSpeech.QUEUE_ADD, null, null);
            }

            // RESTART
            Intent intent = new Intent(this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
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
