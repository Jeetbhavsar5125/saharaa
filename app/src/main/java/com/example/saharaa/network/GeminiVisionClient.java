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

        // 3. Build JSON payload for Gemini API
        JsonObject mimeData = new JsonObject();
        mimeData.addProperty("mime_type", "image/jpeg");
        mimeData.addProperty("data", base64Image);

        JsonObject inlineData = new JsonObject();
        inlineData.add("inline_data", mimeData);

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", userPrompt != null && !userPrompt.isEmpty() ? userPrompt :
                "Identify the main object or product in this image. State its name, brand, or key details clearly in 1 to 2 short sentences for a blind user.");

        JsonArray parts = new JsonArray();
        parts.add(inlineData);
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
                    Log.e(TAG, "Gemini API error response: " + response.code());
                    callback.onError("Gemini API error code: " + response.code());
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
