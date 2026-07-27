package com.example.saharaa.utils;

/**
 * Centralized constants for SharedPreferences keys.
 * Use these instead of raw strings scattered across activities.
 */
public final class AppPrefs {

    // Preferences file names
    public static final String PREFS_MAIN    = "SaharaaPrefs";
    public static final String PREFS_HISTORY = "SaharaaHistory";

    // Keys — main preferences
    public static final String KEY_IS_BLIND      = "IS_BLIND";
    public static final String KEY_LANGUAGE      = "LANGUAGE";
    public static final String KEY_SETUP_DONE    = "SETUP_COMPLETE";
    public static final String KEY_SPEECH_RATE   = "SPEECH_RATE";
    public static final String KEY_GEMINI_API_KEY = "GEMINI_API_KEY";

    // Keys — history
    public static final String KEY_SCAN_RECORDS = "scan_records";

    // Private constructor to prevent instantiation
    private AppPrefs() {}
}
