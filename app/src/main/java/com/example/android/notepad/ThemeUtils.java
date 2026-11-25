package com.example.android.notepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * 处理手动的日间/夜间模式切换，并固定应用内语言。所选模式保存在偏好设置中，
 * 并通过包装每个 Activity 的 base context 来生效，从而让 values-night 资源起作用。
 */
public final class ThemeUtils {

    private static final String PREFS_NAME = "notepad_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemeUtils() {
    }

    static {
        // 日期选择器等系统控件会读取进程默认语言，仅设置 Context 的语言不够，
        // 这里一并固定为简体中文，避免月份名称显示为英文。
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    }

    public static boolean isNightMode(Context context) {
        return preferences(context).getBoolean(KEY_NIGHT_MODE, false);
    }

    public static void setNightMode(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_NIGHT_MODE, enabled).apply();
    }

    /** 返回一个按当前所选日间/夜间模式解析资源，且固定为简体中文的 context。 */
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
        // 固定使用简体中文，避免系统语言为英文时日期选择器等系统弹窗显示英文。
        configuration.setLocale(Locale.SIMPLIFIED_CHINESE);
        // 兼容旧版日期选择器：它读取的是已废弃的 locale 字段，需要一并赋值。
        configuration.locale = Locale.SIMPLIFIED_CHINESE;
        return base.createConfigurationContext(configuration);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
