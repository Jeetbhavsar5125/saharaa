package com.example.saharaa.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InfoParser {

    // Regex Patterns
    // Matches: MRP: 50, Rs. 50, ₹50, Price 50.00, M.R.P. 50
    // Handles common OCR errors: '.' instead of ':', missing spaces
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?:MRP|M\\.R\\.P|M\\s*R\\s*P|Price|Rs\\.?|INR|₹)\\s*[:.]?\\s*(\\d{1,5}(?:[.,]\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    // Matches: 12/2025, 12-08-24, 2025/12, 12.2025
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{2}[/.-]\\d{2}[/.-]\\d{2,4})|(\\d{2}[/.-]\\d{4})");

    // Matches keywords for expiry context, including OCR errors like "E x p"
    private static final Pattern EXPIRY_KEYWORD = Pattern.compile(
            "(?:Exp|Use By|Best Before|Expiry|Date|Mfg|Mfd)",
            Pattern.CASE_INSENSITIVE);

    public static String extractPrice(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1); // Return just the number
        }
        return null; // Not found
    }

    public static String extractExpiryDate(String text) {
        // First look for specific "Exp: Date" pattern
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (EXPIRY_KEYWORD.matcher(line).find()) {
                Matcher dateMatcher = DATE_PATTERN.matcher(line);
                if (dateMatcher.find()) {
                    // group(1) = dd/mm/yyyy style, group(2) = mm/yyyy style
                    return dateMatcher.group(1) != null ? dateMatcher.group(1) : dateMatcher.group(2);
                }
            }
        }
        // Fallback: Just look for any date (less accurate)
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        return null;
    }

    public static String cleanText(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
