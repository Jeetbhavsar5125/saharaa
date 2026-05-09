package com.example.saharaa.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Button;
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
import com.example.saharaa.network.OpenFoodFactsApi;
import com.example.saharaa.network.ProductResponse;
import com.example.saharaa.network.RetrofitClient;
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

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SmartScannerActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private TextView tvMode, tvResult;
    private Button btnCapture;

    private ExecutorService cameraExecutor;
    private boolean isScanning = false; 
    private long scanStartTime = 0;

    private TextToSpeech tts;
    private boolean isBlindMode = false;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isListening = false;
    private boolean isActivityActive = false;

    private BarcodeScanner barcodeScanner;
    private TextRecognizer textRecognizer;

    private int scanMode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_scanner);
        isActivityActive = true;

        viewFinder = findViewById(R.id.viewFinder);
        tvMode = findViewById(R.id.tvMode);
        tvResult = findViewById(R.id.tvResult);
        btnCapture = findViewById(R.id.btnCapture);

        String mode = getIntent().getStringExtra("MODE");
        if ("BARCODE".equals(mode)) {
            scanMode = 1;
            tvMode.setText("Barcode Scanner");
            btnCapture.setText("Scan Barcode");
        } else {
            scanMode = 0;
            tvMode.setText("Text Reader");
            btnCapture.setText("Read Text");
        }

        isBlindMode = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE)
                .getBoolean("IS_BLIND", false);

        barcodeScanner = BarcodeScanning.getClient();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        if (checkPermissions()) {
            startCamera();
        } else {
            requestPermissions();
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {}

                    @Override
                    public void onDone(String id) {
                        if (isBlindMode && isActivityActive) {
                            if ("READY".equals(id) || "RESULT".equals(id) || "LOOP_RETRY".equals(id)
                                    || "MANUAL".equals(id)) {
                                runOnUiThread(() -> startListening());
                            }
                        }
                    }

                    @Override
                    public void onError(String id) {}
                });

                if (isBlindMode) {
                    speak("Scanner ready. Say scan to capture, or back to exit.", "READY");
                }
            }
        });

        if (isBlindMode) {
            initSpeechRecognizer();
        }

        btnCapture.setOnClickListener(v -> startScanningProcess());
    }

    private void startScanningProcess() {
        isScanning = true;
        scanStartTime = System.currentTimeMillis();
        tvResult.setText("Scanning...");
        speak("Scanning started. Please hold steady.", "SCANNING");
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { isListening = true; }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() { isListening = false; }

            @Override
            public void onError(int error) {
                isListening = false;
                if (isActivityActive && isBlindMode) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> startListening(), 1000);
                }
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processCommand(matches.get(0).toLowerCase());
                } else {
                    if (isActivityActive && isBlindMode) startListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        if (speechRecognizer != null && !isListening) {
            runOnUiThread(() -> speechRecognizer.startListening(speechIntent));
        }
    }

    private void processCommand(String command) {
        if (command.contains("scan") || command.contains("capture") || command.contains("read")) {
            startScanningProcess();
        } else if (command.contains("back") || command.contains("exit") || command.contains("close")) {
            speak("Closing scanner", "EXIT");
            finish();
        } else {
            speak("Sorry, say scan to capture, or back to exit.", "LOOP_RETRY");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = {ExperimentalGetImage.class})
    private void analyzeFrame(ImageProxy imageProxy) {
        if (!isScanning || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees());

        if (scanMode == 1) {
            scanBarcode(image, imageProxy);
        } else {
            scanText(image, imageProxy);
        }
    }

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
                    if (!found && isScanning && (System.currentTimeMillis() - scanStartTime > 6000)) {
                        isScanning = false;
                        speak("No barcode found. Please try again.", "RESULT");
                        tvResult.setText("No barcode found");
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void fetchProductFromBarcode(String code) {
        speak("Barcode detected. Fetching product details.", "FETCHING");
        OpenFoodFactsApi api = RetrofitClient.getClient().create(OpenFoodFactsApi.class);
        api.getProduct(code).enqueue(new Callback<ProductResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProductResponse> call, @NonNull Response<ProductResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().status == 1) {
                    ProductResponse.Product p = response.body().product;
                    StringBuilder msg = new StringBuilder();
                    msg.append("Product: ").append(p.productName).append(". ");
                    if (p.brands != null) msg.append("Brand: ").append(p.brands).append(". ");
                    if (p.ingredientsText != null && !p.ingredientsText.isEmpty()) {
                        msg.append("Ingredients: ").append(p.ingredientsText).append(". ");
                    }
                    if (p.nutriments != null && p.nutriments.energyKcal != null) {
                        msg.append("Calories: ").append(p.nutriments.energyKcal).append(" per 100g. ");
                    }
                    tvResult.setText(msg.toString());
                    speak(msg.toString(), "RESULT");
                } else {
                    speak("Product not found.", "RESULT");
                    tvResult.setText("Product not found");
                }
            }
            @Override
            public void onFailure(@NonNull Call<ProductResponse> call, @NonNull Throwable t) {
                speak("Network error", "RESULT");
                tvResult.setText("Network error");
            }
        });
    }

    private void scanText(InputImage image, ImageProxy imageProxy) {
        textRecognizer.process(image)
                .addOnSuccessListener(this::processTextResult)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void processTextResult(Text text) {
        String rawText = text.getText();
        String price = InfoParser.extractPrice(rawText);
        String expiry = InfoParser.extractExpiryDate(rawText);
        if (price != null || expiry != null) {
            isScanning = false;
            StringBuilder msg = new StringBuilder();
            if (price != null) msg.append("Price ").append(price).append(" rupees. ");
            if (expiry != null) msg.append("Expiry ").append(expiry).append(". ");
            tvResult.setText(msg.toString());
            speak(msg.toString(), "RESULT");
        } else {
            String largestBlock = getLargestTextBlock(text);
            if (largestBlock != null && largestBlock.length() > 5) {
                if (System.currentTimeMillis() - scanStartTime > 4000) {
                    isScanning = false;
                    speak("Found text: " + largestBlock, "RESULT");
                    tvResult.setText(largestBlock);
                } else {
                    tvResult.setText("Looking for details... Found: " + largestBlock);
                }
            } else if (isScanning && (System.currentTimeMillis() - scanStartTime > 6000)) {
                isScanning = false;
                speak("No specific text found. Please try again.", "RESULT");
                tvResult.setText("No text found");
            }
        }
    }

    private String getLargestTextBlock(Text text) {
        String largest = null;
        int maxArea = 0;
        if (text.getTextBlocks() != null) {
            for (Text.TextBlock block : text.getTextBlocks()) {
                if (block.getBoundingBox() != null) {
                    int area = block.getBoundingBox().width() * block.getBoundingBox().height();
                    if (area > maxArea) {
                        maxArea = area;
                        largest = block.getText();
                    }
                }
            }
        }
        return largest;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (checkPermissions()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera and Audio permissions are required.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void speak(String text, String id) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        }
    }

    private boolean checkPermissions() {
        int cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return cam == PackageManager.PERMISSION_GRANTED && audio == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO }, 100);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (isBlindMode && tts != null && !isListening) {
            startListening();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        if (tts != null) tts.stop();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            isListening = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (tts != null) tts.shutdown();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (barcodeScanner != null) barcodeScanner.close();
        if (textRecognizer != null) textRecognizer.close();
    }
}
