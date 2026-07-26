package com.example.saharaa.utils;

import android.content.Context;

import com.example.saharaa.db.AppDatabase;
import com.example.saharaa.db.ScanRecordDao;
import com.example.saharaa.model.ScanRecord;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages scan history persistence using Room database.
 *
 * All write operations are executed on a background thread automatically.
 * Read operations must NOT be called on the main thread — use a background executor or
 * Android's LiveData/ViewModel pattern for UI-bound reads.
 *
 * Migration note: previously used Gson + SharedPreferences.
 * Room replaced it in v1.1 to fix potential data corruption on large records.
 */
public final class HistoryManager {

    private static final int MAX_HISTORY = 100;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private HistoryManager() {}

    /**
     * Inserts a new ScanRecord into the Room database (background thread).
     * If total records exceed MAX_HISTORY, oldest are implicitly dropped on next query
     * via the ORDER BY + LIMIT pattern in the DAO.
     */
    public static void addRecord(Context context, ScanRecord record) {
        executor.execute(() -> {
            ScanRecordDao dao = AppDatabase.getInstance(context).scanRecordDao();
            dao.insert(record);
        });
    }

    /**
     * Returns all records (most recent first).
     * MUST be called from a background thread (e.g., inside an AsyncTask, coroutine, or Executor).
     */
    public static List<ScanRecord> getAll(Context context) {
        ScanRecordDao dao = AppDatabase.getInstance(context).scanRecordDao();
        return dao.getRecent(MAX_HISTORY);
    }

    /**
     * Clears all history records (background thread).
     */
    public static void clearAll(Context context) {
        executor.execute(() -> {
            ScanRecordDao dao = AppDatabase.getInstance(context).scanRecordDao();
            dao.clearAll();
        });
    }

    /**
     * Returns total number of records.
     * MUST be called from a background thread.
     */
    public static int count(Context context) {
        ScanRecordDao dao = AppDatabase.getInstance(context).scanRecordDao();
        return dao.count();
    }
}
