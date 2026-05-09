package com.example.saharaa.activities;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.saharaa.R;

import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity(); // Exit app completely
            }
        });

        // 🔹 Animate UI Elements (Modern Approach)
        ImageView logo = findViewById(R.id.imgSplashLogo);
        android.widget.TextView title = findViewById(R.id.tvSplashTitle);
        android.widget.TextView slogan = findViewById(R.id.tvSplashSlogan);

        // Set initial state
        logo.setAlpha(0f);
        logo.setScaleX(0.5f);
        logo.setScaleY(0.5f);

        title.setAlpha(0f);
        title.setTranslationY(50f);

        slogan.setAlpha(0f);
        slogan.setTranslationY(50f);

        // Start Animations
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1500).setStartDelay(300).start();
        title.animate().alpha(1f).translationY(0).setDuration(1200).setStartDelay(800).start();
        slogan.animate().alpha(1f).translationY(0).setDuration(1200).setStartDelay(1200).start();

        // 🔹 Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {

                textToSpeech.setLanguage(Locale.US);

                textToSpeech.setOnUtteranceProgressListener(
                        new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                // voice started
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                // 🔹 After voice → Check setup status
                                runOnUiThread(() -> {
                                    android.content.SharedPreferences prefs = getSharedPreferences("SaharaaPrefs",
                                            MODE_PRIVATE);
                                    boolean setupComplete = prefs.getBoolean("SETUP_COMPLETE", false);

                                    Intent intent;
                                    if (setupComplete) {
                                        intent = new Intent(SplashActivity.this, MainActivity.class);
                                    } else {
                                        intent = new Intent(SplashActivity.this, AccessibilityModeActivity.class);
                                    }
                                    startActivity(intent);
                                    finish();
                                });
                            }

                            @Override
                            public void onError(String utteranceId) {
                            }
                        });

                // 🔹 Speak welcome message
                textToSpeech.speak(
                        "Saharaa. Your voice assistance is starting.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "SPLASH_MSG");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Override
    public void onBackPressed() {
        finishAffinity(); // Exit app completely
    }

}
