package com.teachagain.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * "自动跟随"开关。
 * - 用户显式开启的意愿存本地（enabled）。
 * - 真正生效还需系统里授予无障碍权限（isAccessibilityGranted）。
 * 两者都为真时，GuideAccessibilityService 才会工作。
 */
public class AutoFollowPrefs {

    private static final String PREFS = "auto_follow";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_OCR = "ocr_enabled";

    private final Context appContext;
    private final SharedPreferences prefs;

    public AutoFollowPrefs(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 用户是否开启了自动跟随意愿。 */
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** 是否额外启用 OCR 识别（截当前微信窗口，本机识别）。默认关闭。 */
    public boolean isOcrEnabled() {
        return prefs.getBoolean(KEY_OCR, false);
    }

    public void setOcrEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_OCR, enabled).apply();
    }

    /**
     * 系统是否已授予本应用的无障碍服务。
     * 用 ComponentName 比较，兼容系统可能返回的简写组件名（如 .GuideAccessibilityService）。
     */
    public boolean isAccessibilityGranted() {
        try {
            AccessibilityManager am = (AccessibilityManager)
                    appContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> list =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (list == null) return false;

            ComponentName target = new ComponentName(appContext, GuideAccessibilityService.class);
            for (AccessibilityServiceInfo info : list) {
                String id = info.getId();
                if (id == null) continue;
                ComponentName c = ComponentName.unflattenFromString(id);
                if (c != null && target.equals(c)) {
                    return true;
                }
                // 兜底：用 ResolveInfo.serviceInfo 比较包名+类名
                if (info.getResolveInfo() != null
                        && info.getResolveInfo().serviceInfo != null) {
                    android.content.pm.ServiceInfo si = info.getResolveInfo().serviceInfo;
                    if (target.getPackageName().equals(si.packageName)
                            && target.getClassName().equals(si.name)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** 真正能自动跟随：用户开启意愿 且 系统已授权。 */
    public boolean isActive() {
        return isEnabled() && isAccessibilityGranted();
    }
}
