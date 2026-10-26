package com.gathin.tvhome;
import java.util.ArrayList;
import java.util.List;
final class ReturnGuardPolicy {
    static final String SERVICE = "com.gathin.tvhome/com.gathin.tvhome.ReturnGuardService";
    static boolean shouldArm(boolean enabled, String pkg) {
        return enabled && pkg != null && !pkg.equals("com.xgimi.home")
            && !pkg.equals("com.gathin.tvhome") && !pkg.startsWith("local.projector.");
    }
    static boolean shouldRedirect(boolean enabled, boolean armed, boolean defaultHome, String pkg, String cls) {
        return enabled && armed && defaultHome && "com.xgimi.home".equals(pkg)
            && "com.xgimi.module.cellview.home.ui.HomeActivity".equals(cls);
    }
    static boolean validTiming(long requested, long now, int requestBoot, int currentBoot) {
        return currentBoot >= 0 && currentBoot == requestBoot && requested >= 0
            && now >= requested && now - requested <= 15000;
    }
    static String serviceList(String old, boolean enabled) {
        List<String> items = new ArrayList<>();
        if (old != null) for (String item : old.split(":")) {
            if (!item.isEmpty() && !item.equals(SERVICE) && !item.equals("com.gathin.tvhome/.ReturnGuardService")) items.add(item);
        }
        if (enabled) items.add(SERVICE);
        return String.join(":", items);
    }
}
