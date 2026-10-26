package local.projector.homeswitch;

/** Two fixed HOME choices; no downloaded commands or package removal. */
final class HomePlan {
    static final String CLEAN = "com.gathin.tvhome/com.gathin.tvhome.MainActivity";
    static final String STOCK = "com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity";

    static String target(String action) {
        if ("clean".equals(action)) return CLEAN;
        if ("stock".equals(action)) return STOCK;
        throw new IllegalArgumentException("Unknown HOME action");
    }

    static String script(String run, String action) {
        String target = target(action);
        String dir = RootPlan.directory(run);
        String base = RootPlan.script(run);
        // The EXIT trap still closes SSH and restores temporary maintenance files.
        base = base.replace("  printf '%s\\n' 'cleanup-ran=", "  [ ! -f " + dir + "/home.txt ] || /system/bin/chmod 644 " + dir + "/home.txt\n  printf '%s\\n' 'cleanup-ran=");
        String operation = "exec > " + dir + "/home.txt 2>&1\n"
            + "printf '%s\\n' 'run=" + run + "' 'target=" + target + "'\n"
            + "resolve_home() { /system/bin/timeout 5 /system/bin/cmd package resolve-activity --components --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT; }\n"
            + "normalize() { case \"$1\" in\n"
            + "  'com.gathin.tvhome/.MainActivity'|'" + CLEAN + "') printf '%s' '" + CLEAN + "';;\n"
            + "  '" + STOCK + "') printf '%s' '" + STOCK + "';;\n"
            + "  *) return 1;; esac; }\n"
            + "current_user=$(/system/bin/timeout 5 /system/bin/am get-current-user)\n"
            + "[ \"$current_user\" = 0 ] || { echo 'status=unsupported-user'; exit 41; }\n"
            + "/system/bin/chmod 644 " + dir + "/home.txt\n"
            + "before_raw=$(resolve_home)\n"
            + "if [ \"$before_raw\" = 'android/com.android.internal.app.ResolverActivity' ]; then\n"
            + "  before=resolver; rollback_target='" + STOCK + "'; echo 'fallback=stock-home';\n"
            + "else\n"
            + "  before=$(normalize \"$before_raw\") || { echo 'status=unexpected-original-home'; printf '%s\\n' \"observed=$before_raw\"; exit 42; }\n"
            + "  rollback_target=$before\n"
            + "fi\n"
            + "printf '%s\\n' \"before=$before\"\n"
            + "if [ \"$before\" = '" + target + "' ]; then echo 'status=already-selected'; exit 0; fi\n"
            + "/system/bin/timeout 5 /system/bin/cmd package set-home-activity --user 0 '" + target + "'\n"
            + "set_exit=$?\n"
            + "after_raw=$(resolve_home)\n"
            + "after=$(normalize \"$after_raw\")\n"
            + "printf '%s\\n' \"set_exit=$set_exit\" \"after=$after_raw\"\n"
            + "if [ \"$set_exit\" = 0 ] && [ \"$after\" = '" + target + "' ]; then\n"
            + "  echo 'status=selected'; exit 0\n"
            + "fi\n"
            + "echo 'status=selection-failed-rolling-back'\n"
            + "/system/bin/timeout 5 /system/bin/cmd package set-home-activity --user 0 \"$rollback_target\"\n"
            + "rollback_exit=$?\n"
            + "rollback_raw=$(resolve_home)\n"
            + "rollback=$(normalize \"$rollback_raw\")\n"
            + "printf '%s\\n' \"rollback_exit=$rollback_exit\" \"rollback_home=$rollback_raw\"\n"
            + "if [ \"$rollback_exit\" = 0 ] && [ \"$rollback\" = \"$rollback_target\" ]; then echo 'rollback_verified=true'; else echo 'rollback_verified=false'; fi\n"
            + "exit 43\n";
        return base.substring(0, base.lastIndexOf("exit 0\n")) + operation;
    }
}
