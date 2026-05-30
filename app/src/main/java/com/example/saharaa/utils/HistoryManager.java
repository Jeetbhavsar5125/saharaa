package com.example.saharaa.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.saharaa.model.ScanRecord;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages scan history using SharedPreferences + Gson serialization.
 * Stores up to MAX_RECORDS recent scans (oldest removed when limit reached).
 */
public class HistoryManager {

    private static final String PREFS_NAME   = "SaharaaHistory";
    private static final String KEY_RECORDS  = "scan_records";
    private static final int    MAX_RECORDS  = 50;

    private static final Gson gson = new Gson();

    /** Save a new ScanRecord to history. Trims oldest if over limit. */
    public static void addRecord(Context ctx, ScanRecord record) {
        List<ScanRecord> list = getAll(ctx);
        list.add(0, record);                // newest first
        if (list.size() > MAX_RECORDS) {
            list = list.subList(0, MAX_RECORDS);
        }
        save(ctx, list);
    }

    /** Return all history records, newest first. */
    public static List<ScanRecord> getAll(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_RECORDS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<ScanRecord>>() {}.getType();
        List<ScanRecord> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    /** Wipe all history. */
    public static void clearAll(Context ctx) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit().remove(KEY_RECORDS).apply();
    }

    private static void save(Context ctx, List<ScanRecord> list) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit().putString(KEY_RECORDS, gson.toJson(list)).apply();
    }
}
