package com.example.saharaa.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.saharaa.model.ScanRecord;

/**
 * Room database singleton for the app.
 *
 * Version history:
 *   1 — initial schema (scan_records table)
 */
@Database(entities = {ScanRecord.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /** Returns the DAO for scan records. */
    public abstract ScanRecordDao scanRecordDao();

    /** Returns the singleton instance, creating it if necessary. */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "saharaa_history.db")
                            .fallbackToDestructiveMigration() // safe for v1
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
