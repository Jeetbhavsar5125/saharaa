package com.example.saharaa.network;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Lightweight Google Gemini 1.5 Flash Vision REST Client.
 *
 * Converts camera Bitmaps to compressed JPEG Base64 and sends them
 * to the Gemini 1.5 Flash multimodal endpoint for ultra-smart object recognition.
 */
public class GeminiVisionClient {

    private static final String TAG = "GeminiVisionClient";
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    private static final OkHttpClient client = new OkHttpClient();

    public interface GeminiCallback {
        void onSuccess(String resultText);
        void onError(String errorMessage);
    }

    /**
     * Sends a camera bitmap to Gemini 1.5 Flash Vision API and receives a natural language description.
     *
     * @param bitmap Camera frame bitmap
     * @param apiKey Your Gemini API Key from Google AI Studio
     * @param userPrompt Custom prompt or null for default blind-friendly prompt
     * @param callback Result callback
     */
    public static void analyzeImage(Bitmap bitmap, String apiKey, String userPrompt, GeminiCallback callback) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY")) {
            callback.onError("Gemini API key is not configured.");
            return;
        }

        // 1. Scale down bitmap to max 800px to ensure ultra-fast upload & low latency
        Bitmap scaled = scaleDown(bitmap, 800, true);

        // 2. Compress to JPEG & Base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();
        String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        // 3. Build JSON payload for Gemini API (Must use camelCase: inlineData and mimeType)
        JsonObject mimeData = new JsonObject();
        mimeData.addProperty("mimeType", "image/jpeg");
        mimeData.addProperty("data", base64Image);

        JsonObject inlineDataObj = new JsonObject();
        inlineDataObj.add("inlineData", mimeData);

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", userPrompt != null && !userPrompt.isEmpty() ? userPrompt :
                "You are an AI vision assistant for blind and elderly users. Analyze this image of a product or object and provide a 3-part response spoken in simple, clear sentences:\n" +
                "1. IDENTIFICATION: State the category (e.g., Food Packet, Medicine, Fresh Food, Household Item) and the exact product name with brand.\n" +
                "2. KEY PACKET DETAILS: Read the net weight/quantity, price or MRP, and expiry date if visible on the packet.\n" +
                "3. SAFETY VERDICT: State clearly whether the item appears safe to use or consume, or if there is a warning.\n" +
                "Keep the total answer under 3 to 4 short sentences. Do not use asterisks or formatting symbols.");

        JsonArray parts = new JsonArray();
        parts.add(inlineDataObj);
        parts.add(textPart);

        JsonObject contentObj = new JsonObject();
        contentObj.add("parts", parts);

        JsonArray contentsArray = new JsonArray();
        contentsArray.add(contentObj);

        JsonObject rootJson = new JsonObject();
        rootJson.add("contents", contentsArray);

        // 4. Send HTTP POST request
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                rootJson.toString()
        );

        Request request = new Request.Builder()
                .url(GEMINI_URL + apiKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error calling Gemini API", e);
                callback.onError("Network error calling Gemini AI.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Gemini API error response: " + response.code() + " -> " + errorBody);
                    callback.onError("Gemini API error " + response.code() + ": " + (errorBody.contains("message") ? errorBody : "Check API key or quota"));
                    return;
                }

                String responseBody = response.body().string();
                try {
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    JsonArray candidates = json.getAsJsonArray("candidates");
                    if (candidates != null && candidates.size() > 0) {
                        JsonObject candidate = candidates.get(0).getAsJsonObject();
                        JsonObject content = candidate.getAsJsonObject("content");
                        JsonArray resParts = content.getAsJsonArray("parts");
                        if (resParts != null && resParts.size() > 0) {
                            String reply = resParts.get(0).getAsJsonObject().get("text").getAsString();
                            callback.onSuccess(reply.trim());
                            return;
                        }
                    }
                    callback.onError("No text returned by Gemini AI.");
                } catch (Exception e) {
                    Log.e(TAG, "Parsing error for Gemini response", e);
                    callback.onError("Error parsing AI response.");
                }
            }
        });
    }

    private static Bitmap scaleDown(Bitmap realImage, float maxImageSize, boolean filter) {
        float ratio = Math.min(
                maxImageSize / realImage.getWidth(),
                maxImageSize / realImage.getHeight());
        if (ratio >= 1.0f) return realImage;
        int width = Math.round(ratio * realImage.getWidth());
        int height = Math.round(ratio * realImage.getHeight());
        return Bitmap.createScaledBitmap(realImage, width, height, filter);
    }
}
