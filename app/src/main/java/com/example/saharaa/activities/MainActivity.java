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
    private boolean isBlindUser = false;
    private boolean isListening = false;
    private boolean isActivityActive = false;

    // UI
    private View btnScanObject, btnScanBarcode, btnHistory;
    private FloatingActionButton fabMic;
    // Optional Settings Button field if added to XML later. For now, relying on
    // voice or back flow.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        isActivityActive = true;

        // Init UI
        btnScanObject = findViewById(R.id.btnScanObject);
        btnScanBarcode = findViewById(R.id.btnScanBarcode);
        btnHistory = findViewById(R.id.btnHistory);
        fabMic = findViewById(R.id.fabMic);

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
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        // Listeners
        btnScanObject.setOnClickListener(v -> openObjectScanner());
        btnScanBarcode.setOnClickListener(v -> openBarcodeScanner());
        btnHistory.setOnClickListener(v -> openHistory());
        fabMic.setOnClickListener(v -> {
            speak("Listening...", "MANUAL");
            startListening();
        });

        // Add a Settings button listener if visible, else relying on voice.
        View btnSettings = findViewById(R.id.btnSettings); // Assuming we might add this id to layout
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }
    }

    private void initApp() {
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

                tts.setLanguage(locale);

                // Start loop when TTS is done speaking "Welcome" or "Retry"
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {
                        if (isBlindUser && isActivityActive) {
                            if ("WELCOME".equals(id) || "LOOP_RETRY".equals(id) || "MANUAL".equals(id)) {
                                runOnUiThread(() -> startListening());
                            }
                        }
                    }

                    @Override
                    public void onError(String id) {
                    }
                });

                if (isBlindUser) {
                    speak("Saharaa Vision Ready. Say Scan Product, Barcode, History, or Settings.", "WELCOME");
                }
            }
        });

        // Init SR
        if (isBlindUser) {
            initSpeechRecognizer();
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
            }

            @Override
            public void onBeginningOfSpeech() {
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
                // Auto Loop on Error (No Match / Timeout)
                if (isActivityActive && isBlindUser) {
                    // Slight delay before retry
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        // speak("Listening...", "LOOP_RETRY"); // Optional: Just silence loop
                        startListening();
                    }, 1000);
                }
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processCommand(matches.get(0).toLowerCase());
                } else {
                    if (isActivityActive && isBlindUser)
                        startListening();
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

    private void startListening() {
        if (speechRecognizer != null && !isListening) {
            runOnUiThread(() -> speechRecognizer.startListening(speechIntent));
        }
    }

    private void processCommand(String command) {
        if (command.contains("object") || command.contains("product") || command.contains("item")
                || (command.contains("scan") && !command.contains("barcode"))) {
            speak("Opening Object Scanner", "NAV");
            openObjectScanner();
        } else if (command.contains("barcode") || command.contains("code")) {
            speak("Opening Barcode Scanner", "NAV");
            openBarcodeScanner();
        } else if (command.contains("history") || command.contains("recent")) {
            speak("Opening History", "NAV");
            openHistory();
        } else if (command.contains("setting") || command.contains("config") || command.contains("option")) {
            speak("Opening Settings", "NAV");
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            // Retry
            speak("Sorry, say Scan Product, Barcode, History, or Settings.", "LOOP_RETRY");
        }
    }

    private void openObjectScanner() {
        Intent intent = new Intent(this, SmartScannerActivity.class);
        intent.putExtra("MODE", "OBJECT");
        startActivity(intent);
    }

    private void openBarcodeScanner() {
        Intent intent = new Intent(this, SmartScannerActivity.class);
        intent.putExtra("MODE", "BARCODE");
        startActivity(intent);
    }

    private void openHistory() {
        // Placeholder for History
        Toast.makeText(this, "Opening History...", Toast.LENGTH_SHORT).show();
    }

    private void speak(String msg, String id) {
        if (tts != null) {
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, id);
        }
    }

    // Permissions
    private boolean checkPermissions() {
        int cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return cam == PackageManager.PERMISSION_GRANTED && audio == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO }, PERMISSION_REQUEST_CODE);
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

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (isBlindUser && tts != null && !isListening) {
            // Restart loop if returning to page
            startListening();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        if (tts != null)
            tts.stop();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel(); // Deep stop
            isListening = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null)
            tts.shutdown();
        if (speechRecognizer != null)
            speechRecognizer.destroy();
    }
}
