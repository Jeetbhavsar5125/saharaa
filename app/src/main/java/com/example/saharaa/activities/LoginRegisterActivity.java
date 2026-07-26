package com.example.saharaa.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import com.example.saharaa.R;
import com.example.saharaa.utils.AppPrefs;
import com.example.saharaa.utils.HapticHelper;

public class LoginRegisterActivity extends BaseVoiceActivity {

    private EditText etInput, etOtp;
    private TextView tabLogin, tabRegister;
    private TextView[] otpBoxes;
    private Button btnContinue;
    private ImageView btnFingerprint;

    private boolean isLoginMode = true;

    // ─── BaseVoiceActivity contract ────────────────────────────────────────────

    @Override
    protected String getWelcomeMessage() {
        return getString(R.string.voice_welcome_skip);
    }

    @Override
    protected void processCommand(String spoken) {
        if (spoken.contains("finger") || spoken.contains("print") || spoken.contains("biometric")) {
            handleBiometric();
            return;
        }

        if (spoken.contains("skip") || spoken.contains("guest") || spoken.contains("pass")) {
            speak("Skipping login", "NAV");
            navigateHome();
            return;
        }

        if (spoken.contains("back") || spoken.contains("exit")) {
            speak("Going back", "NAV");
            startActivity(new Intent(this, LanguageSelectionActivity.class));
            finish();
            return;
        }

        String clean = spoken.replaceAll("\\s+", "");

        View otpContainer = findViewById(R.id.otpContainer);
        if (otpContainer != null && otpContainer.getVisibility() == View.GONE) {
            etInput.setText(clean);
            handleContinue();
        } else {
            etOtp.setText(clean);
            handleContinue();
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // BaseVoiceActivity handles TTS/STT + prefs
        setContentView(R.layout.activity_login_register);

        initUI();
        initListeners();
        setLoginMode(true);
    }

    private void initUI() {
        etInput     = findViewById(R.id.etMobile);
        etOtp       = findViewById(R.id.etOtp);
        btnContinue = findViewById(R.id.btnContinue);
        tabLogin    = findViewById(R.id.tabLogin);
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
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                HapticHelper.tap(this);
                startActivity(new Intent(this, LanguageSelectionActivity.class));
                finish();
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                startActivity(new Intent(LoginRegisterActivity.this, LanguageSelectionActivity.class));
                finish();
            }
        });

        // Skip Button
        View btnSkip = findViewById(R.id.btnSkip);
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                HapticHelper.tap(this);
                navigateHome();
            });
        }
    }

    private void initListeners() {
        tabLogin.setOnClickListener(v -> { HapticHelper.tap(this); setLoginMode(true); });
        tabRegister.setOnClickListener(v -> { HapticHelper.tap(this); setLoginMode(false); });

        btnContinue.setOnClickListener(v -> { HapticHelper.tap(this); handleContinue(); });
        btnFingerprint.setOnClickListener(v -> { HapticHelper.tap(this); handleBiometric(); });

        // OTP Text Watcher
        etOtp.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                for (int i = 0; i < 6; i++) {
                    if (i < text.length()) {
                        otpBoxes[i].setText(String.valueOf(text.charAt(i)));
                        otpBoxes[i].setSelected(true);
                    } else {
                        otpBoxes[i].setText("");
                        otpBoxes[i].setSelected(false);
                    }
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Focus Hidden Input on visual tap
        View otpContainer = findViewById(R.id.otpContainer);
        if (otpContainer != null) {
            otpContainer.setOnClickListener(v -> {
                etOtp.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
        }
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
                speak("Please say your mobile or email", "LOOP_RETRY");
            else
                Toast.makeText(this, getString(R.string.error_invalid_input), Toast.LENGTH_SHORT).show();
            return;
        }

        View otpContainer = findViewById(R.id.otpContainer);
        if (otpContainer != null && otpContainer.getVisibility() == View.GONE) {
            if (isValidInput(input)) {
                otpContainer.setVisibility(View.VISIBLE);
                otpBoxes[0].setSelected(true);
                etOtp.requestFocus();

                if (isBlindUser)
                    speak(getString(R.string.voice_otp), "LOOP_RETRY");
            } else {
                if (isBlindUser)
                    speak(getString(R.string.error_invalid_input), "LOOP_RETRY");
                else
                    Toast.makeText(this, getString(R.string.error_invalid_input), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String otp = etOtp.getText().toString().trim();
        // Accepts any valid 6-digit OTP for demo authentication
        if (otp.length() == 6) {
            HapticHelper.success(this);
            if (isBlindUser)
                speak(getString(R.string.voice_success), "SUCCESS");
            navigateHome();
        } else {
            HapticHelper.failure(this);
            if (isBlindUser)
                speak(getString(R.string.voice_retry), "LOOP_RETRY");
            Toast.makeText(this, "Please enter 6-digit code", Toast.LENGTH_SHORT).show();
            etOtp.setText("");
        }
    }

    private boolean isValidInput(String input) {
        boolean isMobile = input.matches("\\d{10}");
        boolean isEmail  = Patterns.EMAIL_ADDRESS.matcher(input).matches();
        return isMobile || isEmail;
    }

    private void handleBiometric() {
        androidx.biometric.BiometricManager biometricManager =
                androidx.biometric.BiometricManager.from(this);
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
                speak(error, "LOOP_RETRY");
            return;
        }

        java.util.concurrent.Executor executor = ContextCompat.getMainExecutor(this);
        androidx.biometric.BiometricPrompt biometricPrompt = new androidx.biometric.BiometricPrompt(this, executor,
                new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (isBlindUser && errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED) {
                            speak("Authentication canceled or error", "LOOP_RETRY");
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        HapticHelper.success(LoginRegisterActivity.this);
                        if (isBlindUser)
                            speak(getString(R.string.msg_biometric_success), "SUCCESS");
                        else {
                            Toast.makeText(LoginRegisterActivity.this,
                                    getString(R.string.msg_biometric_success), Toast.LENGTH_SHORT).show();
                        }
                        navigateHome();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        HapticHelper.failure(LoginRegisterActivity.this);
                    }
                });

        androidx.biometric.BiometricPrompt.PromptInfo promptInfo =
                new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.action_fingerprint))
                .setSubtitle("Saharaa Login")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void navigateHome() {
        getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE)
                .edit().putBoolean(AppPrefs.KEY_SETUP_DONE, true).apply();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
