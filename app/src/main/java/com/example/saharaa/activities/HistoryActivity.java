package com.example.saharaa.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.saharaa.R;
import com.example.saharaa.model.ScanRecord;
import com.example.saharaa.utils.HistoryManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerHistory;
    private LinearLayout emptyState;
    private TextView tvTotalScans, tvBarcodeCount;
    private HistoryAdapter adapter;
    private List<ScanRecord> records;

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isBlindUser = false;
    private boolean isListening = false;
    private boolean isActivityActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        isActivityActive = true;

        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        isBlindUser = prefs.getBoolean("IS_BLIND", false);
        String lang  = prefs.getString("LANGUAGE", "en");

        // UI refs
        recyclerHistory = findViewById(R.id.recyclerHistory);
        emptyState      = findViewById(R.id.emptyState);
        tvTotalScans    = findViewById(R.id.tvTotalScans);
        tvBarcodeCount  = findViewById(R.id.tvBarcodeCount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> clearHistory());

        FloatingActionButton fabMic = findViewById(R.id.fabMic);
        if (isBlindUser) {
            fabMic.setVisibility(View.VISIBLE);
            fabMic.setOnClickListener(v -> {
                speak("Listening", "MANUAL");
                startListening();
            });
        } else {
            fabMic.setVisibility(View.GONE);
        }

        // RecyclerView
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        records = new ArrayList<>(HistoryManager.getAll(this));
        adapter = new HistoryAdapter(records);
        recyclerHistory.setAdapter(adapter);
        updateUI();

        // TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale locale = resolveLocale(lang);
                tts.setLanguage(locale);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) {
                        if (isBlindUser && isActivityActive
                                && ("WELCOME".equals(id) || "LOOP_RETRY".equals(id) || "MANUAL".equals(id))) {
                            runOnUiThread(() -> startListening());
                        }
                    }
                    @Override public void onError(String id) {}
                });

                if (isBlindUser) {
                    int count = records.size();
                    String msg = count == 0
                            ? "Scan History. No scans yet. Say back to go back."
                            : "Scan History. You have " + count + " scans. Say back to go back, or clear to clear history.";
                    speak(msg, "WELCOME");
                }
            }
        });

        if (isBlindUser) initSpeechRecognizer();
    }

    private void updateUI() {
        long barcodeCount = records.stream()
                .filter(r -> ScanRecord.TYPE_BARCODE.equals(r.type)).count();
        tvTotalScans.setText(String.valueOf(records.size()));
        tvBarcodeCount.setText(String.valueOf(barcodeCount));

        if (records.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerHistory.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerHistory.setVisibility(View.VISIBLE);
        }
    }

    private void clearHistory() {
        HistoryManager.clearAll(this);
        records.clear();
        adapter.notifyDataSetChanged();
        updateUI();
        if (isBlindUser) speak("History cleared.", "DONE");
    }

    // ─── TTS Helper ────────────────────────────────────────────────────────────

    private void speak(String msg, String id) {
        if (tts != null) tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    // ─── STT ───────────────────────────────────────────────────────────────────

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { isListening = true; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] buf) {}
            @Override public void onEndOfSpeech() { isListening = false; }

            @Override
            public void onError(int error) {
                isListening = false;
                if (isActivityActive && isBlindUser)
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(() -> startListening(), 1000);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processCommand(matches.get(0).toLowerCase());
                } else if (isActivityActive && isBlindUser) {
                    startListening();
                }
            }

            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}
        });
    }

    private void startListening() {
        if (speechRecognizer != null && !isListening)
            runOnUiThread(() -> speechRecognizer.startListening(speechIntent));
    }

    private void processCommand(String command) {
        if (command.contains("back") || command.contains("exit") || command.contains("close")) {
            speak("Going back.", "NAV");
            finish();
        } else if (command.contains("clear") || command.contains("delete") || command.contains("wipe")) {
            speak("History cleared.", "DONE");
            clearHistory();
        } else {
            speak("Say back to go back, or clear to clear history.", "LOOP_RETRY");
        }
    }

    // ─── Locale helper ─────────────────────────────────────────────────────────

    private Locale resolveLocale(String lang) {
        switch (lang) {
            case "hi": return new Locale("hi", "IN");
            case "gu": return new Locale("gu", "IN");
            default:   return Locale.US;
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (isBlindUser && tts != null && !isListening) startListening();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        if (tts != null) tts.stop();
        if (speechRecognizer != null) { speechRecognizer.stopListening(); speechRecognizer.cancel(); isListening = false; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) tts.shutdown();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }

    // ─── Inner Adapter ─────────────────────────────────────────────────────────

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        private final List<ScanRecord> data;
        private static final SimpleDateFormat sdf =
                new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

        HistoryAdapter(List<ScanRecord> data) { this.data = data; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ScanRecord r = data.get(pos);

            h.tvTitle.setText(r.title != null && !r.title.isEmpty() ? r.title : "Unknown");

            // Subtitle: brand + calories or rawText preview
            StringBuilder sub = new StringBuilder();
            if (r.brand != null && !r.brand.isEmpty()) sub.append(r.brand);
            if (r.calories != null && !r.calories.isEmpty()) {
                if (sub.length() > 0) sub.append("  ·  ");
                sub.append(r.calories).append(" kcal");
            }
            if (sub.length() == 0 && r.rawText != null && !r.rawText.isEmpty()) {
                sub.append(r.rawText.length() > 40 ? r.rawText.substring(0, 40) + "…" : r.rawText);
            }
            h.tvSubtitle.setText(sub.length() > 0 ? sub.toString() : "No extra details");

            h.tvTime.setText(sdf.format(new Date(r.timestamp)));
            h.tvType.setText(r.type != null ? r.type : "Unknown");

            boolean isBarcode = ScanRecord.TYPE_BARCODE.equals(r.type);
            h.ivIcon.setImageResource(isBarcode ? R.drawable.ic_barcode_scanner : R.drawable.ic_scan_object);
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView  tvTitle, tvSubtitle, tvTime, tvType;
            VH(@NonNull View v) {
                super(v);
                ivIcon     = v.findViewById(R.id.ivTypeIcon);
                tvTitle    = v.findViewById(R.id.tvHistoryTitle);
                tvSubtitle = v.findViewById(R.id.tvHistorySubtitle);
                tvTime     = v.findViewById(R.id.tvHistoryTime);
                tvType     = v.findViewById(R.id.tvHistoryType);
            }
        }
    }
}
