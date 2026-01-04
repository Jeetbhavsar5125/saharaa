package com.example.saharaa.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.saharaa.R;

import java.util.ArrayList;
import java.util.Locale;

public class LoginRegisterActivity extends AppCompatActivity {

    private EditText etMobile, etOtp;
    private Button btnLogin, btnRegister;
    private TextToSpeech tts;

    private static final int SPEECH_REQUEST = 301;

    private enum Step { MOBILE, OTP, ACTION }
    private Step currentStep = Step.MOBILE;

    private static final String TEST_MOBILE = "9313094070";
    private static final String TEST_OTP = "1426";

    private boolean isBlindUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_register);

        // Back → Language selection
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                startActivity(new Intent(
                        LoginRegisterActivity.this,
                        LanguageSelectionActivity.class
                ));
                finish();
            }
        });

        etMobile = findViewById(R.id.etMobile);
        etOtp = findViewById(R.id.etOtp);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        isBlindUser = prefs.getBoolean("IS_BLIND", false);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {

                tts.setLanguage(Locale.ENGLISH);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}

                    @Override
                    public void onDone(String id) {
                        if (!isBlindUser) return;

                        runOnUiThread(() -> {
                            switch (id) {
                                case "ASK_MOBILE":
                                case "ASK_OTP":
                                case "ASK_ACTION":
                                case "RETRY":
                                    startListening();
                                    break;

                                case "SUCCESS":
                                    startActivity(new Intent(
                                            LoginRegisterActivity.this,
                                            MainActivity.class
                                    ));
                                    finish();
                                    break;
                            }
                        });
                    }

                    @Override public void onError(String id) {}
                });

                if (isBlindUser) {
                    speak("Please say your mobile number.", "ASK_MOBILE");
                }
            }
        });

        btnLogin.setOnClickListener(v -> tryLogin());
        btnRegister.setOnClickListener(v -> tryLogin());
    }

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

            String spoken = results.get(0).replaceAll("\\s+", "");
            handleSpeechInput(spoken);
        }
    }

    private void handleSpeechInput(String input) {
        switch (currentStep) {

            case MOBILE:
                if (input.length() == 10) {
                    etMobile.setText(input);
                    currentStep = Step.OTP;
                    speak("Mobile number received. Please say your OTP.", "ASK_OTP");
                } else {
                    retry();
                }
                break;

            case OTP:
                if (input.length() >= 4) {
                    etOtp.setText(input);
                    currentStep = Step.ACTION;
                    speak("OTP received. Say login or register.", "ASK_ACTION");
                } else {
                    retry();
                }
                break;

            case ACTION:
                if (input.contains("login") || input.contains("register")) {
                    tryLogin();
                } else {
                    retry();
                }
                break;
        }
    }

    private void retry() {
        speak("Sorry, I did not understand. Please try again.", "RETRY");
    }

    private void tryLogin() {
        String mobile = etMobile.getText().toString().trim();
        String otp = etOtp.getText().toString().trim();

        if (mobile.equals(TEST_MOBILE) && otp.equals(TEST_OTP)) {
            speak("Login successful. Redirecting to home.", "SUCCESS");
        } else {
            speak("Invalid credentials. Please try again.", "RETRY");
            Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
        }
    }

    private void speak(String msg, String id) {
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, id);
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
