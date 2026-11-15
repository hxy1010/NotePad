package com.example.android.notepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

/**
 * Handles the manual day/night switch. The chosen mode is stored in preferences and applied
 * by wrapping each Activity's base context, which makes the values-night resources take effect.
 */
public final class ThemeUtils {

    private static final String PREFS_NAME = "notepad_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemeUtils() {
    }

    public static boolean isNightMode(Context context) {
        return preferences(context).getBoolean(KEY_NIGHT_MODE, false);
    }

    public static void setNightMode(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_NIGHT_MODE, enabled).apply();
    }

    /** Returns a context that resolves resources for the currently selected day/night mode. */
    public static Context wrap(Context base) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return base;
        }
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        int nightMode = isNightMode(base)
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        configuration.uiMode =
                (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return base.createConfigurationContext(configuration);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
