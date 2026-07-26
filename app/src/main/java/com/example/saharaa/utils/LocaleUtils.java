package com.example.saharaa.utils;

import java.util.Locale;

/**
 * Centralized locale resolution for TTS and language settings.
 * Supports all 7 languages offered in the language selection screen.
 */
public final class LocaleUtils {

    private LocaleUtils() {}

    /**
     * Converts a language code string (as stored in SharedPreferences) to a Locale.
     * Falls back to US English for any unknown code.
     *
     * @param lang Language code: "en", "hi", "gu", "es", "fr", "de", "ko"
     * @return Corresponding Locale
     */
    public static Locale resolve(String lang) {
        if (lang == null) return Locale.US;
        switch (lang) {
            case "hi": return new Locale("hi", "IN");
            case "gu": return new Locale("gu", "IN");
            case "es": return new Locale("es", "ES");
            case "fr": return Locale.FRENCH;
            case "de": return Locale.GERMAN;
            case "ko": return Locale.KOREA;
            default:   return Locale.US;
        }
    }
}
