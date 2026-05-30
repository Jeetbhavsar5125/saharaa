package com.example.saharaa.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
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
import com.example.saharaa.network.OpenFoodFactsApi;
import com.example.saharaa.network.ProductResponse;
import com.example.saharaa.network.RetrofitClient;
import com.example.saharaa.utils.HistoryManager;
import com.example.saharaa.utils.InfoParser;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SmartScannerActivity extends AppCompatActivity {

    // ─── UI ────────────────────────────────────────────────────────────────────
    private PreviewView viewFinder;
    private TextView    tvMode;
    private Button      btnCapture;
    private LinearLayout idlePanel, resultPanel, loadingPanel, infoContainer;
    private TextView    tvResultTitle, tvResultType;
    private Button      btnScanAgain, btnSaveToHistory;

    // ─── Camera ────────────────────────────────────────────────────────────────
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private boolean isScanning   = false;
    private long    scanStartTime = 0;

    // ─── ML Kit ────────────────────────────────────────────────────────────────
    private BarcodeScanner barcodeScanner;
    private TextRecognizer textRecognizer;
    private int scanMode = 0; // 0 = text/object, 1 = barcode

    // ─── TTS / STT ─────────────────────────────────────────────────────────────
    private TextToSpeech    tts;
    private SpeechRecognizer speechRecognizer;
    private Intent           speechIntent;
    private boolean isBlindMode      = false;
    private boolean isListening      = false;
    private boolean isActivityActive = false;
    private boolean ttsReady         = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── State ─────────────────────────────────────────────────────────────────
    private ScanRecord pendingRecord = null; // built when result arrives

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_scanner);
        isActivityActive = true;

        // UI refs
        viewFinder    = findViewById(R.id.viewFinder);
        tvMode        = findViewById(R.id.tvMode);
        btnCapture    = findViewById(R.id.btnCapture);
        idlePanel     = findViewById(R.id.idlePanel);
        resultPanel   = findViewById(R.id.resultPanel);
        loadingPanel  = findViewById(R.id.loadingPanel);
        infoContainer = findViewById(R.id.infoContainer);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultType  = findViewById(R.id.tvResultType);
        btnScanAgain  = findViewById(R.id.btnScanAgain);
        btnSaveToHistory = findViewById(R.id.btnSaveToHistory);

        // Mode from intent
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

        // Prefs
        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        isBlindMode = prefs.getBoolean("IS_BLIND", false);
        String lang = prefs.getString("LANGUAGE", "en");

        // Back button
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // ML Kit clients
        barcodeScanner = BarcodeScanning.getClient();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Camera
        if (checkPermissions()) {
            startCamera();
        } else {
            requestPermissions();
        }
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Scan button
        btnCapture.setOnClickListener(v -> startScanningProcess());

        // Build speech intent with fast silence detection
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L);

        // Scan Again
        btnScanAgain.setOnClickListener(v -> resetToIdle());

        // Save to History
        btnSaveToHistory.setOnClickListener(v -> {
            if (pendingRecord != null) {
                HistoryManager.addRecord(this, pendingRecord);
                pendingRecord = null;
                btnSaveToHistory.setEnabled(false);
                btnSaveToHistory.setText("✓  Saved");
                Toast.makeText(this, "Saved to history!", Toast.LENGTH_SHORT).show();
                if (isBlindMode) speak("Saved to history.", "SAVED");
            }
        });

        // FAB Mic
        View fabMic = findViewById(R.id.fabMic);
        if (isBlindMode && fabMic != null) {
            fabMic.setVisibility(View.VISIBLE);
            fabMic.setOnClickListener(v -> {
                if (ttsReady) speak("Listening", "MANUAL");
                startListeningNow();
            });
        }

        // TTS
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(resolveLocale(lang));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                tts.setLanguage(Locale.US);
            ttsReady = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    if (isBlindMode && isActivityActive) {
                        if ("READY".equals(id) || "RESULT".equals(id)
                                || "LOOP_RETRY".equals(id) || "MANUAL".equals(id)) {
                            mainHandler.post(() -> startListeningNow());
                        }
                    }
                }
                @Override public void onError(String id) {}
            });

            if (isBlindMode) {
                // Init STT first, then announce
                initSpeechRecognizer();
                speak("Scanner ready. Press volume up or say scan to capture. Say back to exit.", "READY");
            }
        });

        if (!isBlindMode) initSpeechRecognizer();
    }

    // ─── Scanning ──────────────────────────────────────────────────────────────

    private void startScanningProcess() {
        isScanning    = true;
        scanStartTime = System.currentTimeMillis();
        showLoadingPanel();
        speak("Scanning. Please hold steady.", "SCANNING");
    }

    private void resetToIdle() {
        isScanning    = false;
        pendingRecord = null;
        infoContainer.removeAllViews();
        showIdlePanel();
        resumeCamera();
        if (isBlindMode) speak("Ready to scan again. Press volume up or say scan.", "READY");
    }

    // ─── Volume Key → Scan trigger ─────────────────────────────────────────────

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!isScanning) {
                startScanningProcess();
            }
            return true; // consume the event (don't change volume)
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─── Result Display ────────────────────────────────────────────────────────

    /** Called on the main thread after we have a result to show */
    private void showProductResult(String title, String type,
                                   String[][] rows, // [[label, value], ...]
                                   ScanRecord record) {
        pauseCamera();
        pendingRecord = record;

        tvResultTitle.setText(title);
        tvResultType.setText(type);
        btnSaveToHistory.setEnabled(true);
        btnSaveToHistory.setText("💾  Save");

        // Clear and rebuild key-value rows
        infoContainer.removeAllViews();
        for (String[] row : rows) {
            if (row[1] == null || row[1].isEmpty()) continue;
            addInfoRow(row[0], row[1]);
        }

        showResultPanel();
    }

    /** Inflate a styled key-value row and add to infoContainer */
    private void addInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 10);
        row.setLayoutParams(params);

        // Label
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF8D6E63);
        tvLabel.setTextSize(13f);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);

        // Value
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(0xFF3E2723);
        tvValue.setTextSize(14f);
        tvValue.setMaxLines(3);
        tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
        tvValue.setLayoutParams(valueParams);

        row.addView(tvLabel);
        row.addView(tvValue);
        infoContainer.addView(row);
    }

    // ─── Camera Controls ───────────────────────────────────────────────────────

    private void pauseCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll(); // stop image analysis, preview still OK
        }
    }

    private void resumeCamera() {
        // Simply re-bind everything
        startCamera();
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
        // Slide-up animation
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
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
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
            scanBarcode(image, imageProxy);
        } else {
            scanText(image, imageProxy);
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
                            fetchProductFromBarcode(barcode.getRawValue());
                            break;
                        }
                    }
                    if (!found && isScanning
                            && (System.currentTimeMillis() - scanStartTime > 6000)) {
                        isScanning = false;
                        runOnUiThread(() -> {
                            speak("No barcode found. Please try again.", "RESULT");
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
                            && response.body().status == 1) {
                        ProductResponse.Product p = response.body().product;

                        String name        = p.productName  != null ? p.productName  : "Unknown Product";
                        String brand       = p.brands       != null ? p.brands       : "";
                        String ingredients = p.ingredientsText != null ? p.ingredientsText : "";
                        String calories    = p.nutriments   != null && p.nutriments.energyKcal != null
                                ? p.nutriments.energyKcal + " kcal / 100g" : "";

                        // Voice summary
                        StringBuilder voiceMsg = new StringBuilder();
                        voiceMsg.append("Product: ").append(name).append(". ");
                        if (!brand.isEmpty())    voiceMsg.append("Brand: ").append(brand).append(". ");
                        if (!calories.isEmpty()) voiceMsg.append("Calories: ").append(calories).append(". ");
                        speak(voiceMsg.toString(), "RESULT");

                        // Key-value rows
                        String[][] rows = {
                                {"Product",     name},
                                {"Brand",       brand},
                                {"Calories",    calories},
                                {"Ingredients", ingredients.length() > 120
                                        ? ingredients.substring(0, 120) + "…" : ingredients},
                        };

                        ScanRecord record = new ScanRecord(
                                ScanRecord.TYPE_BARCODE, name, brand, calories, ingredients, null);
                        showProductResult(name, "📦 Barcode Scan", rows, record);

                    } else {
                        speak("Product not found in database.", "RESULT");
                        showIdlePanel();
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<ProductResponse> call, @NonNull Throwable t) {
                runOnUiThread(() -> {
                    speak("Network error. Please check your connection.", "RESULT");
                    showIdlePanel();
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
        String rawText = text.getText();
        String price   = InfoParser.extractPrice(rawText);
        String expiry  = InfoParser.extractExpiryDate(rawText);

        if (price != null || expiry != null) {
            isScanning = false;
            StringBuilder voiceMsg = new StringBuilder();
            if (price  != null) voiceMsg.append("Price ").append(price).append(" rupees. ");
            if (expiry != null) voiceMsg.append("Expiry ").append(expiry).append(". ");
            speak(voiceMsg.toString(), "RESULT");

            String[][] rows = {
                    {"Price",        price  != null ? "₹" + price : null},
                    {"Expiry Date",  expiry},
                    {"Full Text",    rawText.length() > 150
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
            if (largestBlock != null && largestBlock.length() > 5) {
                if (System.currentTimeMillis() - scanStartTime > 4000) {
                    isScanning = false;
                    speak("Found text: " + largestBlock, "RESULT");
                    String[][] rows = {{"Text Found", largestBlock}};
                    ScanRecord record = new ScanRecord(
                            ScanRecord.TYPE_TEXT, largestBlock, null, null, null, largestBlock);
                    runOnUiThread(() -> showProductResult(
                            "Text Detected", "📝 Text Scan", rows, record));
                }
            } else if (isScanning && (System.currentTimeMillis() - scanStartTime > 6000)) {
                isScanning = false;
                runOnUiThread(() -> {
                    speak("No specific text found. Please try again.", "RESULT");
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
                if (area > maxArea) { maxArea = area; largest = block.getText(); }
            }
        }
        return largest;
    }

    // ─── STT ───────────────────────────────────────────────────────────────────

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { isListening = true; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float r) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}
            @Override public void onEndOfSpeech() { isListening = false; }

            @Override
            public void onError(int error) {
                isListening = false;
                if (!isActivityActive || !isBlindMode) return;
                // 0 ms delay for no-match/timeout — restart immediately
                long delay = (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 0L : 300L;
                mainHandler.postDelayed(() -> startListeningNow(), delay);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processCommand(matches.get(0).toLowerCase());
                } else if (isActivityActive && isBlindMode) {
                    startListeningNow();
                }
            }
        });
    }

    /** Cancel any stale session, then start fresh immediately */
    private void startListeningNow() {
        if (!isActivityActive || speechRecognizer == null || speechIntent == null) return;
        if (isListening) { speechRecognizer.cancel(); isListening = false; }
        runOnUiThread(() -> speechRecognizer.startListening(speechIntent));
    }

    private void processCommand(String command) {
        if (command.contains("scan") || command.contains("capture") || command.contains("read")) {
            startScanningProcess();
        } else if (command.contains("save") || command.contains("history")) {
            if (pendingRecord != null) {
                HistoryManager.addRecord(this, pendingRecord);
                speak("Saved to history.", "SAVED");
                pendingRecord = null;
            } else {
                speak("Nothing to save yet.", "LOOP_RETRY");
            }
        } else if (command.contains("again") || command.contains("retry") || command.contains("reset")) {
            resetToIdle();
        } else if (command.contains("back") || command.contains("exit") || command.contains("close")) {
            speak("Closing scanner.", "EXIT");
            finish();
        } else {
            speak("Say scan to capture, save for history, again to retry, or back to exit.", "LOOP_RETRY");
        }
    }

    // ─── Permissions ───────────────────────────────────────────────────────────

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (checkPermissions()) startCamera();
            else {
                Toast.makeText(this, "Camera and Audio permissions required.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // ─── TTS Helper ────────────────────────────────────────────────────────────

    private void speak(String text, String id) {
        if (tts == null || !ttsReady) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    private Locale resolveLocale(String lang) {
        switch (lang) {
            case "hi": return new Locale("hi", "IN");
            case "gu": return new Locale("gu", "IN");
            default:   return Locale.US;
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (speechRecognizer == null) initSpeechRecognizer();
        if (isBlindMode && ttsReady && !isListening)
            mainHandler.postDelayed(() -> startListeningNow(), 300L);
    }

    @Override protected void onPause() {
        super.onPause();
        isActivityActive = false;
        if (tts != null) tts.stop();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            isListening = false;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        cameraExecutor.shutdown();
        if (tts != null) { tts.shutdown(); tts = null; }
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        if (barcodeScanner != null) barcodeScanner.close();
        if (textRecognizer != null) textRecognizer.close();
    }
}
