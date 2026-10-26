package local.projector.returndiag;

import java.util.HashSet;
import java.util.Set;

/** Offline compatibility and result decisions, independent of UI and shell logs. */
final class SwitchPolicy {
    static boolean compatible(int sdk, String device, String hardware, String incremental, String hash, String init) {
        if (sdk != 26 || !"ironman".equals(device) || !"m7221".equals(hardware)
            || !"v12.15.214".equals(incremental)
            || !"b559aa0e7c54ea2b746e0e607c97e6141f68a0c277f507c4dacdc587dec153ad".equals(hash)) return false;
        boolean inside = false; int definitions = 0;
        Set<String> options = new HashSet<>();
        for (String line : init.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (!Character.isWhitespace(line.charAt(0))) {
                inside = trimmed.equals("service xgimi_ssh /vendor/bin/xgimi_ssh.sh");
                if (trimmed.startsWith("service xgimi_ssh ") && !inside) return false;
                if (inside) definitions++;
            } else if (inside && !options.add(trimmed.replaceAll("\\s+", " "))) return false;
        }
        return definitions == 1 && options.size() == 3 && options.contains("class main")
            && options.contains("oneshot") && options.contains("seclabel u:r:xgimi_ssh:s0");
    }
    static boolean success(boolean rootProof, boolean cleaned, String target, String resolved) {
        return rootProof && cleaned && target.equals(resolved);
    }
}
