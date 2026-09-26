package com.limelight.binding.input.touch;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;

/** Persistence for orientation-specific touchpad bounds and appearance. */
public final class TouchpadAreaSettings {
    private static final String PREFS_NAME = "TouchpadArea";
    private static final String BORDER_KEY = "borderVisible";
    private static final String OPACITY_KEY = "backgroundOpacity";

    private TouchpadAreaSettings() {
    }

    public static RectF loadBounds(Context context, boolean landscape) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String prefix = landscape ? "landscape." : "portrait.";
        if (!prefs.contains(prefix + "left")) {
            return defaultBounds();
        }
        return new RectF(prefs.getFloat(prefix + "left", 0.2f),
                prefs.getFloat(prefix + "top", 0.68f),
                prefs.getFloat(prefix + "right", 0.8f),
                prefs.getFloat(prefix + "bottom", 0.96f));
    }

    public static void saveBounds(Context context, boolean landscape, RectF normalizedBounds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String prefix = landscape ? "landscape." : "portrait.";
        prefs.edit().putFloat(prefix + "left", normalizedBounds.left)
                .putFloat(prefix + "top", normalizedBounds.top)
                .putFloat(prefix + "right", normalizedBounds.right)
                .putFloat(prefix + "bottom", normalizedBounds.bottom)
                .apply();
    }

    public static RectF defaultBounds() {
        return new RectF(0.2f, 0.68f, 0.8f, 0.96f);
    }

    public static boolean isBorderVisible(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(BORDER_KEY, true);
    }

    public static float getBackgroundOpacity(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(OPACITY_KEY, 0.18f);
    }

    public static void saveAppearance(Context context, boolean borderVisible, float opacity) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(BORDER_KEY, borderVisible)
                .putFloat(OPACITY_KEY, Math.max(0, Math.min(1, opacity)))
                .apply();
    }
}
