package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.app.Activity;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** Public settings entry points. GMUI exposes its overlay as a service, not an activity. */
final class SystemSettings {
    private static final ComponentName XGIMI_PANEL = new ComponentName("com.android.newsettings",
            "com.xgimi.newsettings.SettingService");
    private static final String XGIMI_PANEL_ACTION = "com.xgimi.settings.SETTINGS";
    private SystemSettings() {}

    static boolean open(Context context, String action) {
        // Verified against NEW Z8X's installed com.android.newsettings 5.02.89 manifest.
        // The normal launcher activity is MockActivity: a single Setting button in its own
        // singleTask task. Starting the exported panel service keeps the caller underneath.
        if (Settings.ACTION_SETTINGS.equals(action) && panelAvailable(context)) {
            try {
                return context.startService(new Intent(XGIMI_PANEL_ACTION).setComponent(XGIMI_PANEL)) != null;
            } catch (SecurityException | IllegalStateException unavailable) {
                return false;
            }
        }
        for (Intent candidate : candidates(context, action)) {
            if (!usable(context, candidate)) continue;
            try {
                Intent launch = new Intent(candidate);
                if (context instanceof Activity) {
                    launch.setFlags(launch.getFlags() & ~(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                } else {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(launch);
                return true;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // An OEM can refuse even a resolvable action. Try the next public entry point.
            }
        }
        return false;
    }

    private static boolean panelAvailable(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            ServiceInfo info = pm.getServiceInfo(XGIMI_PANEL, 0);
            return info.exported && info.enabled && info.applicationInfo.enabled
                    && (info.permission == null || info.permission.isEmpty()
                        || pm.checkPermission(info.permission, context.getPackageName()) == PackageManager.PERMISSION_GRANTED);
        } catch (PackageManager.NameNotFoundException unavailable) {
            return false;
        }
    }

    private static List<Intent> candidates(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        List<Intent> result = new ArrayList<>();
        result.add(new Intent(action));
        // The owner wants the Wi-Fi button to open only the direct wireless page.
        // A missing page must not redirect this button into a vendor/general Settings screen.
        if (Settings.ACTION_WIFI_SETTINGS.equals(action)) return result;
        if (!Settings.ACTION_SETTINGS.equals(action)) result.add(new Intent(Settings.ACTION_SETTINGS));
        // The exact activity differs between GMUI versions. Ask Android for the normal app entry
        // instead of guessing a class name or launching an internal Wi-Fi/factory activity.
        Intent leanback = pm.getLeanbackLaunchIntentForPackage("com.android.newsettings");
        Intent launcher = pm.getLaunchIntentForPackage("com.android.newsettings");
        if (leanback != null) result.add(leanback);
        if (launcher != null) result.add(launcher);
        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component != null) result.add(new Intent().setComponent(component));
        }
        return result;
    }

    private static boolean usable(Context context, Intent candidate) {
        PackageManager pm = context.getPackageManager();
        ResolveInfo resolved = pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.activityInfo == null) return false;
        android.content.pm.ActivityInfo info = resolved.activityInfo;
        return info.exported && info.enabled && info.applicationInfo.enabled
                && !info.name.contains("CTSDummy")
                && !info.name.equals("com.xgimi.newsettings.mock.MockActivity")
                && (info.permission == null || info.permission.isEmpty()
                    || pm.checkPermission(info.permission, context.getPackageName()) == PackageManager.PERMISSION_GRANTED);
    }
}
