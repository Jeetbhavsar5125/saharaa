package com.example.saharaa.model;

public class ScanRecord {
    public static final String TYPE_BARCODE = "Barcode";
    public static final String TYPE_TEXT    = "Text";

    public String type;          // "Barcode" or "Text"
    public String title;         // Product name or first line of text
    public String brand;
    public String calories;
    public String ingredients;
    public String rawText;       // Full result for text scans
    public long   timestamp;     // epoch millis

    // Default constructor needed for Gson
    public ScanRecord() {}

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
