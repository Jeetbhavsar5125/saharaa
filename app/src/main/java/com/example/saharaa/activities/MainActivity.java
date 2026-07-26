package com.example.saharaa.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.saharaa.R;
import com.example.saharaa.utils.HapticHelper;
import com.example.saharaa.utils.ShakeDetector;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends BaseVoiceActivity {

    // UI
    private View btnScanObject, btnScanBarcode, btnHistory;
    private FloatingActionButton fabMic;
    private View tvVoiceStatus;

    // Track if we returned from a child screen (for re-announce on resume)
    private boolean wasEverPaused = false;
    private ShakeDetector shakeDetector;

    // ─── BaseVoiceActivity contract ────────────────────────────────────────────

    @Override
    protected String getWelcomeMessage() {
        return "Saharaa Vision. Say: scan product, barcode, history, or settings.";
    }

    @Override
    protected void processCommand(String command) {
        if (command.contains("help")) {
            speak("Main menu help. Say: scan product to identify items. "
                    + "Say barcode to scan a barcode. "
                    + "Say history to see past scans. "
                    + "Say settings to open settings.", "LOOP_RETRY");
        } else if (command.contains("object") || command.contains("product") || command.contains("item")
                || (command.contains("scan") && !command.contains("barcode"))) {
            speak("Opening scanner.", "NAV");
            openObjectScanner();
        } else if (command.contains("barcode") || command.contains("code")) {
            speak("Opening barcode scanner.", "NAV");
            openBarcodeScanner();
        } else if (command.contains("history") || command.contains("recent")) {
            speak("Opening history.", "NAV");
            openHistory();
        } else if (command.contains("setting") || command.contains("config") || command.contains("option")) {
            speak("Opening settings.", "NAV");
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            speak("Say: scan product, barcode, history, or settings. Say help for all commands.", "LOOP_RETRY");
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // BaseVoiceActivity handles TTS/STT init
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // UI
        btnScanObject  = findViewById(R.id.btnScanObject);
        btnScanBarcode = findViewById(R.id.btnScanBarcode);
        btnHistory     = findViewById(R.id.btnHistory);
        fabMic         = findViewById(R.id.fabMic);
        tvVoiceStatus  = findViewById(R.id.tvVoiceStatus);

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Back handler
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { moveTaskToBack(true); }
        });

        // Click listeners (with safe haptic feedback for elderly users)
        btnScanObject.setOnClickListener(v  -> { HapticHelper.tap(this); openObjectScanner(); });
        btnScanBarcode.setOnClickListener(v -> { HapticHelper.tap(this); openBarcodeScanner(); });
        btnHistory.setOnClickListener(v     -> { HapticHelper.tap(this); openHistory(); });
        fabMic.setOnClickListener(v -> {
            HapticHelper.tap(this);
            speak("Listening", "MANUAL");
            startListeningNow();
        });

        View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                HapticHelper.tap(this);
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }

        shakeDetector = new ShakeDetector(this, () -> {
            HapticHelper.scanStart(this);
            openObjectScanner();
        });
    }

    @Override
    protected void onListeningStateChanged(boolean isListening) {
        if (tvVoiceStatus != null) {
            tvVoiceStatus.setVisibility(isListening ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume(); // handles STT restart
        if (shakeDetector != null) shakeDetector.start();
        if (isBlindUser && ttsReady && wasEverPaused) {
            speak("Saharaa. Say: scan product, barcode, history, or settings.", "WELCOME");
        }
        wasEverPaused = false;
    }

    @Override
    protected void onPause() {
        super.onPause(); // handles TTS/STT cleanup
        if (shakeDetector != null) shakeDetector.stop();
        wasEverPaused = true;
    }

    // ─── Volume key → scan trigger ─────────────────────────────────────────────

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            openObjectScanner();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─── Navigation helpers ────────────────────────────────────────────────────

    private void openObjectScanner() {
        Intent i = new Intent(this, SmartScannerActivity.class);
        i.putExtra("MODE", "OBJECT");
        startActivity(i);
    }

    private void openBarcodeScanner() {
        Intent i = new Intent(this, SmartScannerActivity.class);
        i.putExtra("MODE", "BARCODE");
        startActivity(i);
    }

    private void openHistory() {
        startActivity(new Intent(this, HistoryActivity.class));
    }
}
