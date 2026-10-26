package local.projector.returndiag;
/** Inspects background policy after an optional backed-up change; never changes HOME. */
final class HomePlan {
    static final String CLEAN = "com.gathin.tvhome/com.gathin.tvhome.MainActivity";
    static final String STOCK = "com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity";
    static String target(String action) {
        if (!"pin".equals(action) && !"unpin".equals(action) && !"whitelist".equals(action) && !"unwhitelist".equals(action) && !"inspect".equals(action) && !"clean".equals(action) && !"trial".equals(action) && !"restore".equals(action) && !"grant".equals(action) && !"revoke".equals(action) && !"adbopen".equals(action) && !"adbclose".equals(action)) throw new IllegalArgumentException("Only task diagnostics allowed");
        return CLEAN;
    }
    static String script(String run, String action) { return script(run, action, ""); }
    static String script(String run, String action, String change) {
        target(action);
        String dir = RootPlan.directory(run);
        String base = RootPlan.script(run);
        // Preserve the ProxyCommand stdout pipe: closing it makes SSH see EOF
        // and terminate the command before all snapshots have been collected.
        String operation = "exec 3>&1\n"
            + "exec > " + dir + "/home.txt 2>&1\n"
            + "/system/bin/chmod 644 " + dir + "/home.txt\n"
            + change
            + "echo 'scope=background-policy'\n"
            + "/system/bin/timeout 2 /system/bin/cmd package resolve-activity --components --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT\n"
            + "echo 'activity_processes:'\n"
            + "(\n"
            + "/system/bin/dumpsys -t 5 activity processes\n"
            + "printf '\\nprocesses_rc=%s\\n' \"$?\"\n"
            + ") | (\n"
            + "count=0\n"
            + "while IFS= read -r line; do\n"
            + "case \"$line\" in\n"
            + "*ProcessRecord*com.gathin.tvhome*|*ProcessRecord*local.projector.returndiag*|*ProcessRecord*com.tencent.qqmusictv*|*ProcessRecord*com.xgimi.home*|*mHomeProcess*|*mPreviousProcess*|*mHeavyWeightProcess*|*mProcessLimit*|*mAlwaysFinishActivities*|*mLastMemoryLevel*|*mAllowLowerMemLevel*|*mMaxCachedProcesses*|*mNumNonCachedProcs*|*mNumCachedHiddenProcs*|processes_rc=*)\n"
            + "printf '%s\\n' \"$line\"; count=$((count+1)); [ $count -lt 100 ] || break;;\n"
            + "esac\n"
            + "done\n"
            + ")\n"
            + "echo 'activity_settings:'\n"
            + "(\n"
            + "/system/bin/dumpsys -t 4 activity settings\n"
            + "printf '\\nsettings_rc=%s\\n' \"$?\"\n"
            + ") | (\n"
            + "count=0\n"
            + "while IFS= read -r line; do\n"
            + "case \"$line\" in\n"
            + "*max_cached_processes*|*max_empty_processes*|*CUR_MAX*|*mOverrideMaxCachedProcesses*|*mProcessLimit*|*mAlwaysFinishActivities*|settings_rc=*)\n"
            + "printf '%s\\n' \"$line\"; count=$((count+1)); [ $count -lt 50 ] || break;;\n"
            + "esac\n"
            + "done\n"
            + ")\n"
            + "echo 'memory:'\n"
            + "while IFS= read -r line; do\n"
            + "case \"$line\" in\n"
            + "MemTotal:*|MemFree:*|MemAvailable:*|SwapTotal:*|SwapFree:*) printf '%s\\n' \"$line\";;\n"
            + "esac\n"
            + "done < /proc/meminfo\n"
            + "echo 'logd_service:'\n"
            + "/system/bin/getprop init.svc.logd\n"
            + "echo 'logd_disabled:'\n"
            + "/system/bin/getprop ro.logd.kernel\n"
            + "echo 'events_buffer:'\n"
            + "/system/bin/timeout 3 /system/bin/logcat -b events -g\n"
            + "printf '\\nlog_buffer_rc=%s\\n' \"$?\"\n"
            + "echo 'policy=finished'\n"
            + "echo 'capture=finished'\n"
            + "exit 0\n";
        return base.substring(0, base.lastIndexOf("exit 0\n")) + operation;
    }
}
