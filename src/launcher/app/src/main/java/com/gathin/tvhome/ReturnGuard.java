package com.gathin.tvhome;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
final class ReturnGuard {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("return_guard", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static void arm(Context c, String pkg) {
        prefs(c).edit().putBoolean("armed", ReturnGuardPolicy.shouldArm(enabled(c), pkg)).remove("return_requested_at").remove("event_delay_ms").remove("resume_delay_ms")
            .remove("last_stock_class").remove("return_route").putBoolean("home_cleared_armed", false).commit();
    }
    static void disarm(Context c) { BootHome.consume(c); prefs(c).edit().putBoolean("armed", false).commit(); }
    static void homeResumed(Context c) {
        SharedPreferences p = prefs(c);
        long requested = p.getLong("return_requested_at", -1);
        if (requested >= 0) {
            long now = android.os.SystemClock.uptimeMillis();
            int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            SharedPreferences.Editor edit = p.edit().remove("return_requested_at");
            if (ReturnGuardPolicy.validTiming(requested, now, p.getInt("return_boot", -2), boot))
                edit.putLong("resume_delay_ms", now - requested);
            edit.apply();
        }
        if (p.getBoolean("armed", false)) p.edit().putBoolean("home_cleared_armed", true).apply();
        disarm(c);
    }
    static String timing(Context c) {
        SharedPreferences p = prefs(c);
        String state = "开机记录：" + p.getString("boot_home_status", "尚未收到开机通知") + "\n服务记录：" + p.getString("service_state", "尚未连接");
        if (p.contains("last_stock_class")) state += "\n极米窗口：" + p.getString("last_stock_class", "")
            + "\n收到事件时：待返回=" + p.getBoolean("last_event_armed", false)
            + "，默认简洁=" + p.getBoolean("last_event_default", false)
            + "，开关=" + p.getBoolean("last_event_enabled", false);
        else state += "\n本次尚未收到极米窗口事件";
        if (p.getBoolean("home_cleared_armed", false)) state += "\n本次桌面恢复曾清除待返回状态";
        if (!p.contains("event_delay_ms")) return state + "\n本次未触发自动返回";
        return state + "\n最近返回：事件通知 " + p.getLong("event_delay_ms", 0) + " ms；"
            + (p.contains("resume_delay_ms") ? "发起返回到桌面恢复 " + p.getLong("resume_delay_ms", 0) + " ms" : "尚未记录桌面恢复")
            + "；" + p.getString("return_route", "");
    }
    static boolean isDefault(Context c) {
        ResolveInfo ri = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
    }
    static boolean authorized(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }
    static void setEnabled(Context c, boolean enabled) {
        if (!enabled) { prefs(c).edit().putBoolean("enabled", false).putBoolean("armed", false).commit(); }
        if (!authorized(c)) throw new SecurityException("请先使用返回兼容授权工具授权");
        String old = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String list = ReturnGuardPolicy.serviceList(old, enabled);
        if (!Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list))
            throw new IllegalStateException("服务开关写入失败");
        if (enabled && !Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1))
            throw new IllegalStateException("无障碍总开关写入失败");
        prefs(c).edit().putBoolean("enabled", enabled).putBoolean("armed", false).commit();
    }
}
