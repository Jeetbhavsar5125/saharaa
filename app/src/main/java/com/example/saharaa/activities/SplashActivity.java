package com.example.saharaa.activities;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

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


        // 🔹 Start subtle animation on logo
        ImageView logo = findViewById(R.id.imgSplashLogo);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        logo.startAnimation(fadeIn);

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
                                // 🔹 After voice → go to Accessibility page
                                runOnUiThread(() -> {
                                    Intent intent = new Intent(
                                            SplashActivity.this,
                                            AccessibilityModeActivity.class
                                    );
                                    startActivity(intent);
                                    finish();
                                });
                            }

                            @Override
                            public void onError(String utteranceId) {
                            }
                        }
                );

                // 🔹 Speak welcome message
                textToSpeech.speak(
                        "Saharaa. Your voice assistance is starting.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "SPLASH_MSG"
                );
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
