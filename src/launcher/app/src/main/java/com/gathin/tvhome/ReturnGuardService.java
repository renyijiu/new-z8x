package com.gathin.tvhome;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
/** Only window identity is observed; no view tree, key filtering, polling or network. */
public final class ReturnGuardService extends AccessibilityService {
    static volatile boolean connected;
    @Override protected void onServiceConnected() { connected = true; ReturnGuard.prefs(this).edit().putString("service_state", "已连接").apply(); BootHome.probe(this); }
    @Override public boolean onUnbind(Intent intent) { connected = false; ReturnGuard.prefs(this).edit().putString("service_state", "已解除绑定").apply(); return super.onUnbind(intent); }
    @Override public void onDestroy() { connected = false; ReturnGuard.prefs(this).edit().putString("service_state", "服务已销毁").apply(); super.onDestroy(); }
    @Override public void onInterrupt() { }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        long receivedTime = android.os.SystemClock.uptimeMillis();
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        boolean enabled = ReturnGuard.enabled(this);
        boolean bootReturn = BootHome.pending(this);
        boolean armed = ReturnGuard.prefs(this).getBoolean("armed", false) || bootReturn;
        boolean defaultHome = ReturnGuard.isDefault(this);
        String pkg = String.valueOf(event.getPackageName());
        String cls = String.valueOf(event.getClassName());
        if (!"com.xgimi.home".equals(pkg)) return;
        ReturnGuard.prefs(this).edit().putString("last_stock_class", cls.length() <= 256 ? cls : cls.substring(0, 256))
            .putBoolean("last_event_armed", armed).putBoolean("last_event_default", defaultHome)
            .putBoolean("last_event_enabled", enabled).apply();
        if (!ReturnGuardPolicy.shouldRedirect(enabled, armed, defaultHome, pkg, cls)) return;
        // Consume before launch: repeated window events cannot create a redirect loop.
        ReturnGuard.disarm(this);
        if (bootReturn) ReturnGuard.prefs(this).edit().putString("boot_home_status", "已请求进入简洁桌面").apply();
        long requestTime = android.os.SystemClock.uptimeMillis();
        ReturnGuard.prefs(this).edit()
            .putLong("event_delay_ms", Math.max(0, receivedTime - event.getEventTime()))
            .putLong("return_requested_at", requestTime)
            .putInt("return_boot", android.provider.Settings.Global.getInt(getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1))
            .remove("resume_delay_ms").putString("return_route", bootReturn ? "开机进入" : "直接启动").apply();
        // Restore the route confirmed to work on this firmware. A successful global
        // Home API return only acknowledges input dispatch, not a completed switch.
        try {
            startActivity(new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            ReturnGuard.prefs(this).edit().putString("return_route", "直接启动失败：" + unavailable.getClass().getSimpleName()).apply();
        }
    }
}
