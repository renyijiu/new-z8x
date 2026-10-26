package local.projector.returndiag;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/** One reversible setting change. Original value is committed before root starts. */
final class PolicyTrial {
    static final String KEY = "activity_manager_constants";
    static String withEight(String value) {
        validate(value);
        StringBuilder out = new StringBuilder();
        if (value != null && !value.isEmpty()) for (String item : value.split(",", -1)) {
            int eq = item.indexOf('=');
            if (eq < 1) throw new IllegalArgumentException("后台配置格式不支持，未更改");
            if ("max_cached_processes".equals(item.substring(0, eq).trim())) continue;
            if (out.length() > 0) out.append(',');
            out.append(item);
        }
        if (out.length() > 0) out.append(',');
        return out.append("max_cached_processes=8").toString();
    }
    static void validate(String value) {
        if (value != null && (value.length() > 4096 || value.contains("\n") || value.contains("\r") || value.indexOf(0) >= 0 || "null".equals(value)))
            throw new IllegalArgumentException("后台配置无法安全保存，未更改");
    }
    static String quote(String s) { return "'" + s.replace("'", "'\"'\"'") + "'"; }
    static String command(String before, String target) {
        validate(before); validate(target);
        String expected = before == null ? "null" : before;
        String desired = target == null ? "null" : target;
        return "current=$(/system/bin/settings get global " + KEY + ") || { echo 'change=read-failed'; exit 54; }\n"
            + "[ \"$current\" = " + quote(expected) + " ] || { echo 'change=conflict'; exit 51; }\n"
            + "/system/bin/settings " + (target == null ? "delete global " + KEY : "put global " + KEY + " " + quote(target))
            + " || { echo 'change=write-failed'; exit 52; }\n"
            + "current=$(/system/bin/settings get global " + KEY + ") || { echo 'change=read-failed'; exit 54; }\n"
            + "[ \"$current\" = " + quote(desired) + " ] || { echo 'change=readback-failed'; exit 53; }\n"
            + "echo 'change=verified'\n/system/bin/sleep 2\n";
    }
    static String restoreCommand(String original, String trial) {
        validate(original); validate(trial);
        if (trial == null || !withEight(original).equals(trial))
            throw new IllegalStateException("备份校验失败，停止恢复");
        return "current=$(/system/bin/settings get global " + KEY + ") || { echo 'change=read-failed'; exit 54; }\n"
            + "if [ \"$current\" = " + quote(original == null ? "null" : original) + " ]; then\n"
            + "  echo 'restore=already-original'\necho 'change=verified'\n"
            + "else\n" + command(trial, original)
            + "echo 'restore=restored-original'\nfi\n";
    }
    static String prepare(Context context, String action) {
        String current = Settings.Global.getString(context.getContentResolver(), KEY);
        validate(current);
        SharedPreferences p = context.getSharedPreferences("background_trial", 0);
        if (!p.contains("saved")) {
            if ("restore".equals(action)) throw new IllegalStateException("没有试用记录，无需恢复");
            String trial = withEight(current);
            SharedPreferences.Editor e = p.edit().putBoolean("saved", true).putBoolean("original_absent", current == null).putString("trial", trial);
            if (current != null) e.putString("original", current);
            if (!e.commit()) throw new IllegalStateException("原值保存失败，未更改");
        }
        String original = p.getBoolean("original_absent", false) ? null : p.getString("original", null);
        String trial = p.getString("trial", null);
        if ("restore".equals(action)) return restoreCommand(original, trial);
        if (!java.util.Objects.equals(current, original) && !java.util.Objects.equals(current, trial))
            throw new IllegalStateException("后台配置已被其他操作修改，停止覆盖；原始备份仍保留");
        return command(current, trial);
    }
}
