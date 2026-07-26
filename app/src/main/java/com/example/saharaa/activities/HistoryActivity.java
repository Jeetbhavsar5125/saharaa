package com.example.saharaa.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

public class HistoryActivity extends BaseVoiceActivity {

    private RecyclerView recyclerHistory;
    private LinearLayout emptyState;
    private TextView tvTotalScans, tvBarcodeCount;
    private HistoryAdapter adapter;
    private List<ScanRecord> records;

    // ─── BaseVoiceActivity contract ────────────────────────────────────────────

    @Override
    protected String getWelcomeMessage() {
        int count = records != null ? records.size() : 0;
        if (count == 0) {
            return "Scan History. No scans yet. Say back to go back.";
        } else {
            return "Scan History. You have " + count + " scans. "
                    + "Say back to go back, or clear to clear history.";
        }
    }

    @Override
    protected void processCommand(String command) {
        if (command.contains("back") || command.contains("exit") || command.contains("close")) {
            speak("Going back.", "NAV");
            finish();
        } else if (command.contains("clear") || command.contains("delete") || command.contains("wipe")) {
            clearHistory();
            speak("History cleared.", "LOOP_RETRY");
        } else {
            speak("Say back to go back, or clear to clear history.", "LOOP_RETRY");
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // BaseVoiceActivity loads isBlindUser, lang, TTS/STT
        setContentView(R.layout.activity_history);

        // UI refs
        recyclerHistory = findViewById(R.id.recyclerHistory);
        emptyState      = findViewById(R.id.emptyState);
        tvTotalScans    = findViewById(R.id.tvTotalScans);
        tvBarcodeCount  = findViewById(R.id.tvBarcodeCount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> {
            clearHistory();
            speak("History cleared.", "LOOP_RETRY");
        });

        FloatingActionButton fabMic = findViewById(R.id.fabMic);
        if (isBlindUser) {
            fabMic.setVisibility(View.VISIBLE);
            fabMic.setOnClickListener(v -> {
                speak("Listening", "MANUAL");
                startListeningNow();
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
    }

    // ─── UI helpers ────────────────────────────────────────────────────────────

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
