package com.example.saharaa.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a single scan result.
 * Also used as a plain POJO in non-Room contexts (kept backward-compatible).
 */
@Entity(tableName = "scan_records")
public class ScanRecord {

    public static final String TYPE_BARCODE = "Barcode";
    public static final String TYPE_TEXT    = "Text";

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "type")
    public String type;          // "Barcode" or "Text"

    @ColumnInfo(name = "title")
    public String title;         // Product name or first line of text

    @ColumnInfo(name = "brand")
    public String brand;

    @ColumnInfo(name = "calories")
    public String calories;

    @ColumnInfo(name = "ingredients")
    public String ingredients;

    @ColumnInfo(name = "raw_text")
    public String rawText;       // Full result for text scans

    @ColumnInfo(name = "timestamp")
    public long timestamp;       // epoch millis

    /** Required by Room */
    public ScanRecord() {}

    /** Convenience constructor (no-id, used everywhere we create a new record). */
    public ScanRecord(String type, String title, String brand,
                      String calories, String ingredients, String rawText) {
        this.type        = type;
        this.title       = title;
        this.brand       = brand;
        this.calories    = calories;
        this.ingredients = ingredients;
        this.rawText     = rawText;
        this.timestamp   = System.currentTimeMillis();
    }
}
