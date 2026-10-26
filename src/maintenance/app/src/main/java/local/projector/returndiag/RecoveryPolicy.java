package local.projector.returndiag;
final class RecoveryPolicy {
    static boolean matches(String journalRun, String savedRun) {
        return journalRun.matches("[a-f0-9]{32}") && journalRun.equals(savedRun);
    }
    static boolean complete(boolean root, boolean cleaned, String log) {
        if (!root || !cleaned || !log.contains("capture=finished\n")) return false;
        if (log.contains("policy=finished\n"))
            return log.contains("processes_rc=0\n") && log.contains("settings_rc=0\n");
        if (log.contains("scope=filtered-process-events"))
            return log.contains("events_rc=0\n") && log.contains("events=finished\n")
                && !log.contains("logcat read failure");
        return log.contains("snapshot=12\n");
    }
}
