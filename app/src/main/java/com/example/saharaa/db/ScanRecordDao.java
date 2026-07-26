package com.example.saharaa.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.saharaa.model.ScanRecord;

import java.util.List;

/**
 * Data Access Object for ScanRecord.
 * All database operations go through this interface — Room generates the implementation.
 */
@Dao
public interface ScanRecordDao {

    /** Insert a new record. Ignores conflicts (duplicate IDs). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(ScanRecord record);

    /** Returns all records ordered by most recent first. */
    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC")
    List<ScanRecord> getAll();

    /** Returns the N most recent records. */
    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC LIMIT :limit")
    List<ScanRecord> getRecent(int limit);

    /** Delete all records. */
    @Query("DELETE FROM scan_records")
    void clearAll();

    /** Total count of records. */
    @Query("SELECT COUNT(*) FROM scan_records")
    int count();
}
