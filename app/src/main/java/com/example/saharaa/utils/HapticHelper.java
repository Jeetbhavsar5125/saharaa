package com.example.saharaa.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Centralized haptic feedback helper.
 *
 * Patterns for blind/elderly users:
 *   - 1 pulse (short)  → scan started / button tapped
 *   - 2 pulses         → scan found / success
 *   - 3 pulses (long)  → scan failed / error
 */
public final class HapticHelper {

    private HapticHelper() {}

    /** Single short pulse — scan initiated or interaction confirmed. */
    public static void scanStart(Context context) {
        vibrate(context, new long[]{0, 80});
    }

    /** Double pulse — result found / save confirmed. */
    public static void success(Context context) {
        vibrate(context, new long[]{0, 80, 100, 80});
    }

    /** Triple pulse — no result found / error. */
    public static void failure(Context context) {
        vibrate(context, new long[]{0, 120, 80, 120, 80, 120});
    }

    /** Soft single tap — for general UI interactions. */
    public static void tap(Context context) {
        vibrate(context, new long[]{0, 40});
    }

    @SuppressWarnings("deprecation")
    private static void vibrate(Context context, long[] pattern) {
        if (context == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(
                        Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    Vibrator v = vm.getDefaultVibrator();
                    if (v != null && v.hasVibrator()) {
                        v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
            } else {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(pattern, -1);
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("HapticHelper", "Vibration failed safely: " + t.getMessage());
        }
    }
}
