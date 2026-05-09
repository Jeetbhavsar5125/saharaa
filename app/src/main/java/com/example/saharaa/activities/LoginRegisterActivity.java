package com.example.saharaa.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.saharaa.R;

import java.util.ArrayList;
import java.util.Locale;

public class LoginRegisterActivity extends AppCompatActivity {

    private EditText etInput, etOtp;
    private TextView tabLogin, tabRegister;
    private TextView[] otpBoxes;
    private Button btnContinue;
    private ImageView btnFingerprint;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final String TEST_MOBILE = "9313094070";
    private static final String TEST_EMAIL = "test@saharaa.com";
    private static final String TEST_OTP = "123456";

    private boolean isBlindUser = false;
    private boolean isLoginMode = true;
    private boolean isListening = false;

    // Localization
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        String lang = prefs.getString("LANGUAGE", "en");
        Locale locale = new Locale(lang);
        if (lang.equals("hi"))
            locale = new Locale("hi", "IN");
        if (lang.equals("gu"))
            locale = new Locale("gu", "IN");

        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_register);

        initUI();
        initListeners();
        setupToggle();

        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        isBlindUser = prefs.getBoolean("IS_BLIND", false);
        String lang = prefs.getString("LANGUAGE", "en");

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale locale = new Locale(lang);
                if (lang.equals("hi"))
                    locale = new Locale("hi", "IN");
                if (lang.equals("gu"))
                    locale = new Locale("gu", "IN");

                tts.setLanguage(locale);
                tts.setSpeechRate(0.85f);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {
                        if (!isBlindUser)
                            return;
                        runOnUiThread(() -> {
                            if (id.startsWith("ASK_") || id.equals("RETRY")) {
                                startListening();
                            }
                            if (id.equals("BIO_SUCCESS")) {
                                navigateHome();
                            }
                        });
                    }

                    @Override
                    public void onError(String id) {
                    }
                });

                // Check permissions before welcoming
                if (isBlindUser) {
                    checkAndRequestPermissions();
                }
            }
        });

        if (isBlindUser) {
            initSpeechRecognizer();
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
                isListening = true;
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                isListening = false;
            }

            @Override
            public void onError(int error) {
                isListening = false;
                // Auto Retry on No Match or Timeout
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    speak(getString(R.string.voice_retry), "RETRY");
                } else {
                    // Other errors (network, etc) - maybe just wait or silent retry
                    // speak("Error occurred", "ERROR");
                }
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processVoiceResult(matches.get(0));
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.RECORD_AUDIO }, PERMISSION_REQUEST_CODE);
        } else {
            speak(getString(R.string.voice_welcome_skip), "ASK_INPUT");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                speak(getString(R.string.voice_welcome_skip), "ASK_INPUT");
            } else {
                Toast.makeText(this, "Microphone permission required for voice commands", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initUI() {
        etInput = findViewById(R.id.etMobile);
        etOtp = findViewById(R.id.etOtp);
        btnContinue = findViewById(R.id.btnContinue);
        tabLogin = findViewById(R.id.tabLogin);
        tabRegister = findViewById(R.id.tabRegister);
        btnFingerprint = findViewById(R.id.btnFingerprint);

        // OTP Boxes
        otpBoxes = new TextView[6];
        otpBoxes[0] = findViewById(R.id.otpDigit1);
        otpBoxes[1] = findViewById(R.id.otpDigit2);
        otpBoxes[2] = findViewById(R.id.otpDigit3);
        otpBoxes[3] = findViewById(R.id.otpDigit4);
        otpBoxes[4] = findViewById(R.id.otpDigit5);
        otpBoxes[5] = findViewById(R.id.otpDigit6);

        // Back Button
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            startActivity(new Intent(LoginRegisterActivity.this, LanguageSelectionActivity.class));
            finish();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                startActivity(new Intent(LoginRegisterActivity.this, LanguageSelectionActivity.class));
                finish();
            }
        });

        // Skip Button
        findViewById(R.id.btnSkip).setOnClickListener(v -> navigateHome());
    }

    private void initListeners() {
        tabLogin.setOnClickListener(v -> setLoginMode(true));
        tabRegister.setOnClickListener(v -> setLoginMode(false));

        btnContinue.setOnClickListener(v -> handleContinue());
        btnFingerprint.setOnClickListener(v -> handleBiometric());

        // OTP Text Watcher
        etOtp.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                for (int i = 0; i < 6; i++) {
                    if (i < text.length()) {
                        otpBoxes[i].setText(String.valueOf(text.charAt(i)));
                        // Optional: Change styling to "Filled"
                        otpBoxes[i].setSelected(true);
                    } else {
                        otpBoxes[i].setText("");
                        otpBoxes[i].setSelected(false);
                    }
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        // Focus Hidden Input on visual tap
        findViewById(R.id.otpContainer).setOnClickListener(v -> {
            etOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(
                    INPUT_METHOD_SERVICE);
            imm.showSoftInput(etOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void setupToggle() {
        setLoginMode(true);
    }

    private void setLoginMode(boolean login) {
        isLoginMode = login;
        if (login) {
            tabLogin.setBackgroundResource(R.drawable.bg_button_primary);
            tabLogin.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.brown_primary));
            tabLogin.setTextColor(getColor(R.color.white));
            tabRegister.setBackgroundResource(android.R.color.transparent);
            tabRegister.setTextColor(getColor(R.color.text_secondary_light));
        } else {
            tabRegister.setBackgroundResource(R.drawable.bg_button_primary);
            tabRegister.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.brown_primary));
            tabRegister.setTextColor(getColor(R.color.white));
            tabLogin.setBackgroundResource(android.R.color.transparent);
            tabLogin.setTextColor(getColor(R.color.text_secondary_light));
        }
    }

    private void handleContinue() {
        String input = etInput.getText().toString().trim();

        if (input.isEmpty()) {
            if (isBlindUser)
                speak("Please say your mobile or email", "RETRY");
            else
                Toast.makeText(this, getString(R.string.error_invalid_input), Toast.LENGTH_SHORT).show();
            return;
        }

        if (findViewById(R.id.otpContainer).getVisibility() == android.view.View.GONE) {
            if (isValidInput(input)) {
                // Show Visual OTP Container
                findViewById(R.id.otpContainer).setVisibility(android.view.View.VISIBLE);

                // Focus styling for first box
                otpBoxes[0].setSelected(true);

                etOtp.requestFocus(); // Focus hidden input for typing

                if (isBlindUser)
                    speak(getString(R.string.voice_otp), "ASK_OTP");
            } else {
                if (isBlindUser)
                    speak(getString(R.string.error_invalid_input), "RETRY");
                else
                    Toast.makeText(this, getString(R.string.error_invalid_input), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String otp = etOtp.getText().toString().trim();
        if ((input.equals(TEST_MOBILE) || input.equals(TEST_EMAIL)) && otp.equals(TEST_OTP)) {
            if (isBlindUser)
                speak(getString(R.string.voice_success), "SUCCESS");
            navigateHome();
        } else {
            if (isBlindUser)
                speak(getString(R.string.voice_retry), "RETRY");
            Toast.makeText(this, "Invalid Code", Toast.LENGTH_SHORT).show();
            etOtp.setText(""); // Clear on fail
        }
    }

    private boolean isValidInput(String input) {
        boolean isMobile = input.matches("\\d{10}");
        boolean isEmail = Patterns.EMAIL_ADDRESS.matcher(input).matches();
        return isMobile || isEmail;
    }

    // Real Biometric Logic
    private void handleBiometric() {
        // Check Hardware
        androidx.biometric.BiometricManager biometricManager = androidx.biometric.BiometricManager.from(this);
        int canAuthenticate = biometricManager
                .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG);

        if (canAuthenticate != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            String error = "Fingerprint not available";
            if (canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
                error = "No sensor found";
            if (canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
                error = "No fingerprint registered";

            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            if (isBlindUser)
                speak(error, "BIO_ERROR");
            return;
        }

        java.util.concurrent.Executor executor = ContextCompat.getMainExecutor(this);
        androidx.biometric.BiometricPrompt biometricPrompt = new androidx.biometric.BiometricPrompt(this, executor,
                new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // System UI handles visual error.
                        if (isBlindUser && errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED) {
                            speak("Authentication canceled or error", "BIO_ERROR");
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        if (isBlindUser)
                            speak(getString(R.string.msg_biometric_success), "BIO_SUCCESS");
                        else {
                            Toast.makeText(LoginRegisterActivity.this, getString(R.string.msg_biometric_success),
                                    Toast.LENGTH_SHORT).show();
                            navigateHome();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // System UI shakes/shows error.
                    }
                });

        androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.action_fingerprint))
                .setSubtitle("Saharaa Login")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void navigateHome() {
        // SAVE SETUP COMPLETE
        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("SETUP_COMPLETE", true).apply();

        startActivity(new Intent(LoginRegisterActivity.this, MainActivity.class));
        finish();
    }

    private void startListening() {
        if (speechRecognizer != null && !isListening) {
            runOnUiThread(() -> speechRecognizer.startListening(speechRecognizerIntent));
        }
    }

    // Process results from SpeechRecognizer
    private void processVoiceResult(String spoken) {
        spoken = spoken.toLowerCase();

        if (spoken.contains("finger") || spoken.contains("print") || spoken.contains("biometric")) {
            handleBiometric();
            return;
        }

        if (spoken.contains("skip") || spoken.contains("guest") || spoken.contains("pass")) {
            speak("Skipping login", "NAV_HOME");
            navigateHome();
            return;
        }

        String clean = spoken.replaceAll("\\s+", "");

        if (etOtp.getVisibility() == android.view.View.GONE) {
            etInput.setText(clean);
            handleContinue();
        } else {
            etOtp.setText(clean);
            handleContinue();
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
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
