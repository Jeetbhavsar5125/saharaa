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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends BaseVoiceActivity {

    // UI
    private View btnScanObject, btnScanBarcode, btnHistory;
    private FloatingActionButton fabMic;

    // Track if we returned from a child screen (for re-announce on resume)
    private boolean wasEverPaused = false;

    // ─── BaseVoiceActivity contract ────────────────────────────────────────────

    @Override
    protected String getWelcomeMessage() {
        return "Saharaa Vision. Say: scan product, barcode, history, or settings.";
    }

    @Override
    protected void processCommand(String command) {
        if (command.contains("object") || command.contains("product") || command.contains("item")
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
            speak("Say: scan product, barcode, history, or settings.", "LOOP_RETRY");
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

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Back handler
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishAffinity(); }
        });

        // Click listeners
        btnScanObject.setOnClickListener(v  -> openObjectScanner());
        btnScanBarcode.setOnClickListener(v -> openBarcodeScanner());
        btnHistory.setOnClickListener(v     -> openHistory());
        fabMic.setOnClickListener(v -> {
            speak("Listening", "MANUAL");
            startListeningNow();
        });

        View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume(); // handles STT restart
        if (isBlindUser && ttsReady && wasEverPaused) {
            speak("Saharaa. Say: scan product, barcode, history, or settings.", "WELCOME");
        }
        wasEverPaused = false;
    }

    @Override
    protected void onPause() {
        super.onPause(); // handles TTS/STT cleanup
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
