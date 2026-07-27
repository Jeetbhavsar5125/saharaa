package com.example.saharaa.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.saharaa.R;
import com.example.saharaa.model.ScanRecord;
import com.example.saharaa.network.GeminiVisionClient;
import com.example.saharaa.network.OpenFoodFactsApi;
import com.example.saharaa.network.ProductResponse;
import com.example.saharaa.network.RetrofitClient;
import com.example.saharaa.utils.AppPrefs;
import com.example.saharaa.utils.HapticHelper;
import com.example.saharaa.utils.HistoryManager;
import com.example.saharaa.utils.InfoParser;
import com.example.saharaa.utils.ShakeDetector;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Scanner screen — handles barcode scanning and OCR text reading.
 * Extends BaseVoiceActivity for TTS/STT management.
 *
 * Camera lifecycle note: camera is deliberately NOT managed by BaseVoiceActivity
 * since it requires special pause/resume handling around scan results.
 */
public class SmartScannerActivity extends BaseVoiceActivity {

    private static final int PERMISSION_CODE = 100;
    private static final String TAG = "SmartScanner";

    // ─── UI ────────────────────────────────────────────────────────────────────
    private PreviewView  viewFinder;
    private TextView     tvMode;
    private Button       btnCapture;
    private LinearLayout idlePanel, resultPanel, loadingPanel, infoContainer;
    private TextView     tvResultTitle, tvResultType;
    private Button       btnScanAgain, btnSaveToHistory;

    // ─── Camera ────────────────────────────────────────────────────────────────
    private ExecutorService       cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private androidx.camera.core.Camera camera; // kept for torch control
    private boolean isScanning    = false;
    private boolean torchOn       = false;
    private long    scanStartTime = 0;

    // ─── ML Kit ────────────────────────────────────────────────────────────────
    private BarcodeScanner barcodeScanner;
    private TextRecognizer textRecognizer;
    private int scanMode = 0; // 0 = text/OCR, 1 = barcode

    // ─── State ─────────────────────────────────────────────────────────────────
    private ScanRecord pendingRecord = null;
    private View tvVoiceStatus;
    private ShakeDetector shakeDetector;

    // ─── BaseVoiceActivity contract ────────────────────────────────────────────

    @Override
    protected String getWelcomeMessage() {
        return "Scanner ready. Press volume up or say scan to capture. Say back to exit.";
    }

    /** Scanner has additional TTS IDs that should trigger STT restart. */
    @Override
    protected String[] getLoopBackIds() {
        return new String[]{"WELCOME", "READY", "RESULT_PROMPT", "LOOP_RETRY", "MANUAL", "SAVED"};
    }

    /** Called once TTS is ready — start camera and announce ready state. */
    @Override
    protected void onTtsReady() {
        // Nothing extra needed — BaseVoiceActivity speaks getWelcomeMessage() automatically.
    }

    @Override
    protected void processCommand(String command) {
        if (command.contains("help")) {
            speak("Scanner help. Say: scan to capture. Save for history. Again to rescan. "
                    + "Light on or light off for flashlight. Back to exit.", "LOOP_RETRY");
        } else if (command.contains("scan") || command.contains("capture") || command.contains("read")) {
            speak("Scanning now.", "SCANNING");
            startScanningProcess();
        } else if (command.contains("save") || command.contains("history")) {
            if (pendingRecord != null) {
                HistoryManager.addRecord(this, pendingRecord);
                pendingRecord = null;
                runOnUiThread(() -> {
                    btnSaveToHistory.setEnabled(false);
                    btnSaveToHistory.setText("✓  Saved");
                });
                speak("Saved to history. Say scan again or back.", "SAVED");
            } else {
                speak("Nothing to save. Say scan again or back.", "LOOP_RETRY");
            }
        } else if (command.contains("again") || command.contains("retry") || command.contains("reset")) {
            resetToIdle();
        } else if (command.contains("light") || command.contains("torch") || command.contains("flash")) {
            if (command.contains("off") || (torchOn && !command.contains("on"))) {
                setTorch(false);
                speak("Flashlight off.", "LOOP_RETRY");
            } else {
                setTorch(true);
                speak("Flashlight on.", "LOOP_RETRY");
            }
        } else if (command.contains("back") || command.contains("exit") || command.contains("close")) {
            speak("Going back.", "EXIT");
            finish();
        } else {
            speak("Say: scan, save, again, light on or off, or back.", "LOOP_RETRY");
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // BaseVoiceActivity: reads prefs, inits TTS/STT
        setContentView(R.layout.activity_smart_scanner);

        // UI refs
        viewFinder       = findViewById(R.id.viewFinder);
        tvMode           = findViewById(R.id.tvMode);
        btnCapture       = findViewById(R.id.btnCapture);
        idlePanel        = findViewById(R.id.idlePanel);
        resultPanel      = findViewById(R.id.resultPanel);
        loadingPanel     = findViewById(R.id.loadingPanel);
        infoContainer    = findViewById(R.id.infoContainer);
        tvResultTitle    = findViewById(R.id.tvResultTitle);
        tvResultType     = findViewById(R.id.tvResultType);
        btnScanAgain     = findViewById(R.id.btnScanAgain);
        btnSaveToHistory = findViewById(R.id.btnSaveToHistory);

        // Scan mode from intent
        String mode = getIntent().getStringExtra("MODE");
        if ("BARCODE".equals(mode)) {
            scanMode = 1;
            tvMode.setText("Barcode Scanner");
            btnCapture.setText("📷  Scan Barcode");
        } else {
            scanMode = 0;
            tvMode.setText("Text Reader");
            btnCapture.setText("📷  Read Text");
        }

        // Back button
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // ML Kit clients
        barcodeScanner = BarcodeScanning.getClient();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Scan button
        btnCapture.setOnClickListener(v -> startScanningProcess());

        // Scan Again
        btnScanAgain.setOnClickListener(v -> resetToIdle());

        // Save to History
        btnSaveToHistory.setOnClickListener(v -> saveCurrentRecord());

        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);

        // FAB Mic (always visible for hands-free voice trigger)
        View fabMic = findViewById(R.id.fabMic);
        if (fabMic != null) {
            fabMic.setVisibility(View.VISIBLE);
            fabMic.setOnClickListener(v -> {
                speak("Listening", "MANUAL");
                startListeningNow();
            });
        }

        // Camera
        if (checkCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE);
        }

        shakeDetector = new ShakeDetector(this, () -> {
            if (!isScanning) {
                startScanningProcess();
            }
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
        super.onResume(); // BaseVoiceActivity restarts STT
        if (shakeDetector != null) shakeDetector.start();
    }

    @Override
    protected void onPause() {
        super.onPause(); // BaseVoiceActivity stops TTS/STT
        if (shakeDetector != null) shakeDetector.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy(); // BaseVoiceActivity cleans up TTS/STT/handlers
        cameraExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();
        if (textRecognizer != null) textRecognizer.close();
    }

    // ─── Permission handling ────────────────────────────────────────────────────

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // ─── Volume key → scan ─────────────────────────────────────────────────────

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            if (!isScanning) startScanningProcess();
            return true; // consume — don't change volume
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─── Scan flow ─────────────────────────────────────────────────────────────

    private void startScanningProcess() {
        isScanning    = true;
        scanStartTime = System.currentTimeMillis();
        HapticHelper.scanStart(this); // P3-1: 1-pulse haptic
        showLoadingPanel();
        speak("Scanning. Please hold steady.", "SCANNING");
    }

    private void resetToIdle() {
        isScanning    = false;
        pendingRecord = null;
        infoContainer.removeAllViews();
        showIdlePanel();
        resumeCamera();
        if (isBlindUser) speak("Ready to scan again. Press volume up or say scan.", "READY");
    }

    private void saveCurrentRecord() {
        if (pendingRecord != null) {
            HistoryManager.addRecord(this, pendingRecord);
            pendingRecord = null;
            btnSaveToHistory.setEnabled(false);
            btnSaveToHistory.setText("✓  Saved");
            Toast.makeText(this, "Saved to history!", Toast.LENGTH_SHORT).show();
            if (isBlindUser) speak("Saved to history. Say scan again or back.", "SAVED");
        } else {
            Toast.makeText(this, "Already saved.", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── Result display ────────────────────────────────────────────────────────

    private void showProductResult(String title, String type, String[][] rows, ScanRecord record) {
        pauseCamera();
        pendingRecord = record;

        tvResultTitle.setText(title);
        tvResultType.setText(type);
        btnSaveToHistory.setEnabled(true);
        btnSaveToHistory.setText("💾  Save");

        infoContainer.removeAllViews();
        for (String[] row : rows) {
            if (row[1] == null || row[1].isEmpty()) continue;
            addInfoRow(row[0], row[1]);
        }
        showResultPanel();
    }

    private void addInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 10);
        row.setLayoutParams(params);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF8D6E63);
        tvLabel.setTextSize(13f);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(0xFF3E2723);
        tvValue.setTextSize(14f);
        tvValue.setMaxLines(3);
        tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        row.addView(tvLabel);
        row.addView(tvValue);
        infoContainer.addView(row);
    }

    // ─── Panel visibility ──────────────────────────────────────────────────────

    private void showIdlePanel() {
        idlePanel.setVisibility(View.VISIBLE);
        loadingPanel.setVisibility(View.GONE);
        resultPanel.setVisibility(View.GONE);
    }

    private void showLoadingPanel() {
        idlePanel.setVisibility(View.GONE);
        loadingPanel.setVisibility(View.VISIBLE);
        resultPanel.setVisibility(View.GONE);
    }

    private void showResultPanel() {
        idlePanel.setVisibility(View.GONE);
        loadingPanel.setVisibility(View.GONE);
        resultPanel.setVisibility(View.VISIBLE);
        resultPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));
    }

    // ─── Camera ────────────────────────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                cameraProvider.unbindAll();
                // Keep camera reference for torch control
                camera = cameraProvider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void pauseCamera() {
        if (cameraProvider != null) cameraProvider.unbindAll();
    }

    private void resumeCamera() {
        startCamera();
    }

    /** Toggle the camera flashlight. Safe no-op if device has no torch. */
    private void setTorch(boolean on) {
        if (camera != null) {
            camera.getCameraControl().enableTorch(on);
            torchOn = on;
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(ImageProxy imageProxy) {
        if (!isScanning || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        if (scanMode == 1) {
            // STRICT BARCODE SCANNING ONLY
            scanBarcode(image, imageProxy);
        } else {
            // OBJECT / TEXT SCANNING: Check if Gemini AI Key is set for AI Multimodal Object Recognition
            String geminiApiKey = getSharedPreferences(AppPrefs.PREFS_MAIN, MODE_PRIVATE)
                    .getString(AppPrefs.KEY_GEMINI_API_KEY, null);

            android.graphics.Bitmap bitmap = viewFinder.getBitmap();
            if (geminiApiKey != null && !geminiApiKey.isEmpty() && bitmap != null) {
                isScanning = false;
                imageProxy.close();
                runOnUiThread(() -> {
                    speak("Analyzing object with AI. Please hold steady.", "FETCHING");
                    showLoadingPanel();
                });

                GeminiVisionClient.analyzeImage(bitmap, geminiApiKey, null, new GeminiVisionClient.GeminiCallback() {
                    @Override
                    public void onSuccess(String resultText) {
                        runOnUiThread(() -> {
                            HapticHelper.success(SmartScannerActivity.this);
                            speak(resultText + ". Say scan again, save, or back.", "RESULT_PROMPT");
                            String[][] rows = {
                                    {"AI Object Description", resultText},
                                    {"AI Engine", "Gemini 1.5 Flash Vision"}
                            };
                            ScanRecord record = new ScanRecord(ScanRecord.TYPE_TEXT, resultText, "Gemini AI", null, null, resultText);
                            showProductResult(resultText, "✨ Gemini AI Object Identification", rows, record);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            // Fallback to local OCR scanning
                            isScanning = true;
                        });
                    }
                });
            } else {
                // STRICT TEXT / OCR SCANNING ONLY (Local Offline Mode)
                scanText(image, imageProxy);
            }
        }
    }

    // ─── Barcode ───────────────────────────────────────────────────────────────

    private void scanBarcode(InputImage image, ImageProxy imageProxy) {
        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    boolean found = false;
                    for (Barcode barcode : barcodes) {
                        if (barcode.getRawValue() != null) {
                            isScanning = false;
                            found = true;
                            HapticHelper.success(this);
                            fetchProductFromBarcode(barcode.getRawValue());
                            break;
                        }
                    }
                    if (!found && isScanning
                            && (System.currentTimeMillis() - scanStartTime > 8000)) {
                        isScanning = false;
                        HapticHelper.failure(this);
                        runOnUiThread(() -> {
                            speak("No barcode detected. Please adjust lighting or move closer, then tap scan.",
                                    "RESULT_PROMPT");
                            showIdlePanel();
                        });
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void fetchProductFromBarcode(String code) {
        speak("Barcode detected. Fetching product details.", "FETCHING");
        runOnUiThread(this::showLoadingPanel);

        OpenFoodFactsApi api = RetrofitClient.getClient().create(OpenFoodFactsApi.class);
        api.getProduct(code).enqueue(new Callback<ProductResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProductResponse> call,
                                   @NonNull Response<ProductResponse> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().status == 1 && response.body().product != null) {
                        ProductResponse.Product p = response.body().product;

                        String name        = p.productName     != null && !p.productName.isEmpty() ? p.productName     : "Product " + code;
                        String brand       = p.brands          != null ? p.brands          : "";
                        String ingredients = p.ingredientsText != null ? p.ingredientsText : "";
                        String calories    = p.nutriments != null && p.nutriments.energyKcal != null
                                ? p.nutriments.energyKcal + " kcal / 100g" : "";

                        // Voice summary
                        StringBuilder voiceMsg = new StringBuilder();
                        voiceMsg.append("Product found. ").append(name).append(". ");
                        if (!brand.isEmpty())    voiceMsg.append("Brand: ").append(brand).append(". ");
                        if (!calories.isEmpty()) voiceMsg.append("Calories: ").append(calories).append(". ");
                        voiceMsg.append("Say: scan again, save to history, or back.");
                        speak(voiceMsg.toString(), "RESULT_PROMPT");

                        String[][] rows = {
                                {"Product",     name},
                                {"Brand",       brand},
                                {"Barcode",     code},
                                {"Calories",    calories},
                                {"Ingredients", ingredients.length() > 120
                                        ? ingredients.substring(0, 120) + "…" : ingredients},
                        };

                        String ingredientsSaved = ingredients.length() > 500
                                ? ingredients.substring(0, 500) + "…" : ingredients;
                        ScanRecord record = new ScanRecord(
                                ScanRecord.TYPE_BARCODE, name, brand, calories, ingredientsSaved, null);
                        showProductResult(name, "📦 Barcode Scan", rows, record);

                    } else {
                        // Fallback result card for valid barcode not in OpenFoodFacts database
                        String name = "Barcode: " + code;
                        String voiceMsg = "Barcode scanned: " + code + ". Product details not found in online database. Say scan again or save.";
                        speak(voiceMsg, "RESULT_PROMPT");

                        String[][] rows = {
                                {"Barcode Code", code},
                                {"Database Status", "Not found in OpenFoodFacts database"}
                        };

                        ScanRecord record = new ScanRecord(
                                ScanRecord.TYPE_BARCODE, name, "Scanned Barcode", null, null, code);
                        showProductResult(name, "📦 Barcode Scan", rows, record);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<ProductResponse> call, @NonNull Throwable t) {
                runOnUiThread(() -> {
                    String name = "Barcode: " + code;
                    String voiceMsg = "Barcode scanned: " + code + ". Network error while fetching details. Say scan again or back.";
                    speak(voiceMsg, "RESULT_PROMPT");

                    String[][] rows = {
                            {"Barcode Code", code},
                            {"Network Status", "Offline / Network Error"}
                    };

                    ScanRecord record = new ScanRecord(
                            ScanRecord.TYPE_BARCODE, name, "Offline Scan", null, null, code);
                    showProductResult(name, "📦 Barcode Scan", rows, record);
                });
            }
        });
    }

    // ─── OCR / Text ────────────────────────────────────────────────────────────

    private void scanText(InputImage image, ImageProxy imageProxy) {
        textRecognizer.process(image)
                .addOnSuccessListener(this::processTextResult)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void processTextResult(Text text) {
        if (!isScanning) return;

        String rawText = text.getText();
        String price   = InfoParser.extractPrice(rawText);
        String expiry  = InfoParser.extractExpiryDate(rawText);

        if (price != null || expiry != null) {
            isScanning = false;
            HapticHelper.success(this);

            StringBuilder voiceMsg = new StringBuilder();
            if (price  != null) voiceMsg.append("Price: ").append(price).append(" rupees. ");
            if (expiry != null) voiceMsg.append("Expiry: ").append(expiry).append(". ");
            voiceMsg.append("Say: scan again, save to history, or back.");
            speak(voiceMsg.toString(), "RESULT_PROMPT");

            String[][] rows = {
                    {"Price",       price  != null ? "₹" + price : null},
                    {"Expiry Date", expiry},
                    {"Full Text",   rawText.length() > 150
                            ? rawText.substring(0, 150) + "…" : rawText},
            };
            ScanRecord record = new ScanRecord(
                    ScanRecord.TYPE_TEXT, price != null ? "₹" + price : expiry,
                    null, null, null, rawText);
            runOnUiThread(() -> showProductResult(
                    price != null ? "Price: ₹" + price : "Expiry: " + expiry,
                    "📝 Text Scan", rows, record));

        } else {
            String largestBlock = getLargestTextBlock(text);
            long elapsed = System.currentTimeMillis() - scanStartTime;

            if (largestBlock != null && largestBlock.length() > 3 && elapsed > 1200) {
                isScanning = false;
                HapticHelper.success(this);

                String msg = "Found text: " + largestBlock + ". Say: scan again, save, or back.";
                speak(msg, "RESULT_PROMPT");
                String[][] rows = {
                        {"Detected Text", largestBlock},
                        {"Full Output", rawText.length() > 150 ? rawText.substring(0, 150) + "…" : rawText}
                };
                ScanRecord record = new ScanRecord(
                        ScanRecord.TYPE_TEXT, largestBlock, null, null, null, rawText);
                runOnUiThread(() -> showProductResult(
                        "Text Detected", "📝 Object / Text Scan", rows, record));

            } else if (isScanning && elapsed > 7000) {
                isScanning = false;
                HapticHelper.failure(this);
                runOnUiThread(() -> {
                    speak("No readable text found. Hold camera steady and try again.", "RESULT_PROMPT");
                    showIdlePanel();
                });
            }
        }
    }

    private String getLargestTextBlock(Text text) {
        String largest = null;
        int maxArea = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            if (block.getBoundingBox() != null) {
                int area = block.getBoundingBox().width() * block.getBoundingBox().height();
                if (area > maxArea && block.getText().trim().length() > 3) {
                    maxArea = area;
                    largest = block.getText().trim();
                }
            }
        }
        return largest;
    }
}
