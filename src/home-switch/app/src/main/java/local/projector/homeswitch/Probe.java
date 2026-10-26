package local.projector.homeswitch;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class Probe {
    interface Progress { void show(String message); }
    private final Context context;
    private static final String VENDOR_HASH = "b559aa0e7c54ea2b746e0e607c97e6141f68a0c277f507c4dacdc587dec153ad";
    private final Progress progress;
    private String action;
    private static final String SERVICE_SCRIPT = "/vendor/bin/xgimi_ssh.sh";
    private static final String PARAMS = "/data/ssh/params";

    Probe(Context context, Progress progress) throws Exception {
        this.context = context;
        this.progress = progress;
    }

    JSONObject run(String requestedAction) throws Exception {
        HomePlan.target(requestedAction);
        action = requestedAction;
        File oldJournal = new File(context.getFilesDir(), "journal.json");
        if (oldJournal.isFile() && new JSONObject(read(oldJournal.getPath(), 32768)).optBoolean("pending", true))
            throw new IllegalStateException("上次清理尚未完成，请先点恢复检查");
        String run = UUID.randomUUID().toString().replace("-", "");
        JSONObject preflight = preflight(run);
        save("preflight.json", preflight);
        JSONObject journal = new JSONObject().put("run_id", run).put("pending", true)
            .put("directory_before", preflight.getJSONObject("directory_before")).put("home_action", action);
        save("journal.json", journal);
        JSONObject result = new JSONObject().put("schema", 1).put("run_id", run)
            .put("app_uid", android.os.Process.myUid()).put("attempted", true).put("root_verified", false)
            .put("home_action", action).put("home_target", HomePlan.target(action));
        try {
            File dir = new File(RootPlan.directory(run));
            if (!dir.mkdir()) throw new java.io.IOException("无法创建独立验证目录");
            Os.chmod(dir.getPath(), 0700);
            createExclusive(new File(dir, "probe.sh"), HomePlan.script(run, action), 0700);
            createExclusive(new File(PARAMS), RootPlan.params(run) + "\n", 0600);
            progress.show("正在设置默认桌面并核对……");
            set("xgimi.remoteDebug.on", "true");
            command("/system/bin/setprop", "ctl.start", "xgimi_ssh");
            for (int i = 0; i < 300; i++) {
                // Root creates the marker with umask 077, then chmods it.
                // Existence alone races the chmod and can make reading fail.
                if (new File(dir, "cleanup.txt").canRead() && new File(dir, "proof.txt").canRead()) break;
                Thread.sleep(250);
            }
            File proof = new File(dir, "proof.txt");
            String evidence = proof.isFile() ? read(proof.getPath(), 2048) : "";
            result.put("proof", evidence);
            int uid = -1;
            if (evidence.contains("run=" + run + "\n")) {
                for (String line : evidence.split("\n")) if (line.matches("uid=[0-9]+")) uid = Integer.parseInt(line.substring(4));
            }
            int proofOwner = -1;
            boolean regularProof = false;
            if (proof.exists()) {
                StructStat proofStat = Os.lstat(proof.getPath());
                proofOwner = proofStat.st_uid;
                regularProof = OsConstants.S_ISREG(proofStat.st_mode);
            }
            result.put("proof_owner_uid", proofOwner).put("proof_regular_file", regularProof);
            result.put("root_uid", uid).put("root_verified", uid == 0 && proofOwner == 0 && regularProof);
            File homeLog = new File(dir, "home.txt");
            result.put("home_log", homeLog.canRead() ? read(homeLog.getPath(), 49152) : "");
            File marker = new File(dir, "cleanup.txt");
            result.put("root_cleanup_marker", marker.isFile() ? read(marker.getPath(), 2048) : "");
        } catch (Exception e) {
            // Preserve already verified UID/ownership evidence if a later marker read fails.
            result.put("error", e.toString());
        } finally {
            progress.show("正在关闭维护服务并复查……");
            try { result.put("cleanup", cleanup(journal)); }
            catch (Exception e) {
                result.put("cleanup", new JSONObject().put("verified", false)
                    .put("errors", new JSONArray().put(e.toString())));
            }
            save("last-result.json", result);
        }
        JSONObject finalInspection = result.optJSONObject("cleanup") == null ? null
            : result.getJSONObject("cleanup").optJSONObject("inspection_after");
        result.put("home_verified", SwitchPolicy.success(result.optBoolean("root_verified"),
            result.getJSONObject("cleanup").optBoolean("verified"), HomePlan.target(action),
            finalInspection == null ? "unknown" : finalInspection.optString("resolved_home")));
        save("last-result.json", result);
        return result;
    }

    JSONObject recover() throws Exception {
        File file = new File(context.getFilesDir(), "journal.json");
        if (!file.isFile()) throw new IllegalStateException("没有需要恢复的操作记录");
        JSONObject journal = new JSONObject(read(file.getPath(), 32768));
        JSONObject result = new JSONObject().put("recovery", true);
        if (journal.optBoolean("pending", true)) result.put("cleanup", cleanup(journal));
        else result.put("cleanup", new JSONObject().put("verified", servicesClosed()
            && directoryState().toString().equals(journal.getJSONObject("directory_before").toString()))
            .put("inspection_after", Inspection.inspect(context)));
        save("last-result.json", result);
        return result;
    }

    private JSONObject preflight(String run) throws Exception {
        if (!SwitchPolicy.compatible(android.os.Build.VERSION.SDK_INT, android.os.Build.DEVICE,
            android.os.Build.HARDWARE, android.os.Build.VERSION.INCREMENTAL,
            hash(read(SERVICE_SCRIPT, 16384)), read("/vendor/etc/init/hw/init.xgimi.rc", 131072)))
            throw new IllegalStateException("仅支持已验证的 NEW Z8X 固件 v12.15.214，当前固件或维护服务不兼容");
        for (String component : new String[]{HomePlan.CLEAN, HomePlan.STOCK}) {
            android.content.pm.ActivityInfo a = context.getPackageManager().getActivityInfo(
                android.content.ComponentName.unflattenFromString(component), 0);
            if (!a.enabled || !a.applicationInfo.enabled || !a.exported)
                throw new IllegalStateException("请先安装并启用两个桌面");
        }
        android.content.ComponentName target = android.content.ComponentName.unflattenFromString(HomePlan.target(action));
        android.content.pm.ActivityInfo activity = context.getPackageManager().getActivityInfo(target, 0);
        if (!activity.enabled || !activity.applicationInfo.enabled || !activity.exported) {
            throw new IllegalStateException("目标桌面不可用，未进行更改");
        }
        String script = read(SERVICE_SCRIPT, 16384);
        String hash = hash(script);
        if (!hash.equals(VENDOR_HASH)) throw new IllegalStateException("维护脚本与前次检查不同，已停止。");
        if (!prop("xgimi.remoteDebug.on").equals("false") || !prop("init.svc.xgimi_ssh").equals("stopped")) {
            throw new IllegalStateException("维护服务原本已开启，本工具不会接管现有会话。");
        }
        if (!prop("init.svc.adbd").equals("stopped") || portOpen(22) || portOpen(5555)) {
            throw new IllegalStateException("调试端口原本已开启，需先核对现有会话。");
        }
        for (String name : new String[]{"id", "sh", "setprop", "getprop", "pidof", "killall", "sleep", "cat", "rm", "chmod", "timeout", "cmd", "am"}) {
            if (!new File("/system/bin/" + name).canExecute()) throw new IllegalStateException("缺少预期系统命令：" + name);
        }
        JSONObject processes = commandResult("/system/bin/pidof", "sshd");
        if (processes.getInt("exit") > 1 || !processes.getString("text").isEmpty()) {
            throw new IllegalStateException("发现现有 SSH 进程或无法核实进程，已停止。");
        }
        JSONObject before = directoryState();
        if (!before.getString("mode").equals("0777") || before.getInt("uid") != 0 || before.getInt("gid") != 0) {
            throw new IllegalStateException("维护目录权限与前次检查不符");
        }
        JSONObject entries = before.getJSONObject("entries");
        if (entries.has("params") || entries.has("p") || entries.has("authorized_keys")
            || new File(RootPlan.directory(run)).exists()) throw new IllegalStateException("存在维护文件，已停止以保留原配置。");
        String[] expected = {"ssh_host_dsa_key", "ssh_host_dsa_key.pub", "ssh_host_rsa_key", "ssh_host_rsa_key.pub"};
        if (entries.length() != expected.length) throw new IllegalStateException("维护目录存在其他文件，请先核对");
        for (String name : expected) {
            JSONObject e = entries.optJSONObject(name);
            if (e == null || e.optInt("uid", -1) != 0 || !OsConstants.S_ISREG(e.getInt("mode")) || e.optLong("size") <= 0)
                throw new IllegalStateException("维护主机密钥元数据与预期不符");
        }
        JSONObject result = new JSONObject().put("schema", 1).put("run_id", run)
            .put("app_uid", android.os.Process.myUid()).put("script_sha256", hash)
            .put("home_action", action).put("home_target", HomePlan.target(action))
            .put("vendor_script", script).put("sshd_config", read("/system/etc/ssh/sshd_config", 16384))
            .put("payload", HomePlan.script(run, action)).put("payload_sha256", hash(HomePlan.script(run, action)))
            .put("directory_before", before).put("inspection", Inspection.inspect(context));
        return result;
    }

    private JSONObject cleanup(JSONObject journal) throws Exception {
        String run = journal.getString("run_id");
        RootPlan.directory(run); // Validate before touching paths.
        JSONArray errors = new JSONArray();
        // Only these two nonpersistent service controls are restored. No ADB settings change.
        try { set("xgimi.remoteDebug.on", "false"); } catch (Exception e) { errors.put(e.toString()); }
        try { command("/system/bin/setprop", "ctl.stop", "xgimi_ssh"); } catch (Exception e) { errors.put(e.toString()); }
        if (portOpen(22) || sshdPresent()) {
            // The inspected vendor script's false branch kills its SSH children.
            // oneshot children survived ctl.stop on this firmware, so explicitly
            // invoke that existing stop branch, with the exact reviewed source.
            try {
                if (!hash(read(SERVICE_SCRIPT, 16384)).equals(VENDOR_HASH)) {
                    throw new IllegalStateException("维护脚本已变化，未调用停止分支");
                }
                if (!prop("xgimi.remoteDebug.on").equals("false")) throw new IllegalStateException("维护开关未关闭");
                command("/system/bin/setprop", "ctl.start", "xgimi_ssh");
                for (int i = 0; i < 20 && (portOpen(22) || sshdPresent()); i++) Thread.sleep(250);
                command("/system/bin/setprop", "ctl.stop", "xgimi_ssh");
            } catch (Exception e) { errors.put(e.toString()); }
        }
        for (int i = 0; i < 12 && portOpen(22); i++) Thread.sleep(250);
        File params = new File(PARAMS);
        if (params.exists()) {
            try {
                StructStat s = Os.lstat(PARAMS);
                if (!OsConstants.S_ISREG(s.st_mode) || s.st_uid != android.os.Process.myUid()
                    || !read(PARAMS, 4096).trim().equals(RootPlan.params(run))) {
                    errors.put("params 不是本次写入的文件，未删除");
                } else if (!params.delete()) errors.put("params 删除失败");
            } catch (Exception e) { errors.put(e.toString()); }
        }
        // The vendor script may create this file; baseline validation requires its absence.
        File auth = new File("/data/ssh/authorized_keys");
        if (auth.exists() && !journal.getJSONObject("directory_before").getJSONObject("entries").has("authorized_keys")) {
            try {
                if (!OsConstants.S_ISREG(Os.lstat(auth.getPath()).st_mode) || !auth.delete()) errors.put("维护服务生成的 authorized_keys 未清理");
            } catch (Exception e) { errors.put(e.toString()); }
        }
        File dir = new File(RootPlan.directory(run));
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) {
                if (!Arrays.asList("probe.sh", "proof.txt", "cleanup.txt", "home.txt").contains(f.getName()) || !f.delete()) errors.put("验证文件未清理：" + f.getName());
            }
            if (!dir.delete()) errors.put("验证目录未清理");
        }
        JSONObject after = directoryState();
        boolean directoryRestored = after.toString().equals(journal.getJSONObject("directory_before").toString());
        JSONObject inspection = Inspection.inspect(context);
        boolean closed = servicesClosed();
        JSONObject result = new JSONObject().put("errors", errors).put("directory_restored", directoryRestored)
            .put("directory_after", after).put("services_closed", closed)
            .put("verified", errors.length() == 0 && directoryRestored && closed).put("inspection_after", inspection);
        journal.put("pending", !result.getBoolean("verified"));
        save("journal.json", journal);
        return result;
    }

    private static boolean sshdPresent() throws Exception {
        JSONObject result = commandResult("/system/bin/pidof", "sshd");
        if (result.getInt("exit") > 1) throw new java.io.IOException("无法核实 SSH 进程");
        return !result.getString("text").isEmpty();
    }

    private static boolean servicesClosed() throws Exception {
        JSONObject processes = commandResult("/system/bin/pidof", "sshd");
        return !portOpen(22) && !portOpen(5555)
            && prop("xgimi.remoteDebug.on").equals("false") && prop("init.svc.xgimi_ssh").equals("stopped")
            && prop("init.svc.adbd").equals("stopped")
            && processes.getInt("exit") <= 1 && processes.getString("text").isEmpty();
    }

    private static JSONObject directoryState() throws Exception {
        StructStat s = Os.lstat("/data/ssh");
        if (!OsConstants.S_ISDIR(s.st_mode)) throw new IllegalStateException("维护目录类型异常");
        String[] names = new File("/data/ssh").list();
        if (names == null || names.length > 100) throw new IllegalStateException("无法核对维护目录");
        Arrays.sort(names);
        JSONObject entries = new JSONObject();
        for (String name : names) {
            StructStat child = Os.lstat("/data/ssh/" + name);
            entries.put(name, new JSONObject().put("uid", child.st_uid).put("gid", child.st_gid)
                .put("mode", child.st_mode).put("size", child.st_size).put("inode", child.st_ino));
        }
        return new JSONObject().put("uid", s.st_uid).put("gid", s.st_gid)
            .put("mode", String.format(Locale.ROOT, "%04o", s.st_mode & 07777)).put("entries", entries);
    }

    private static boolean portOpen(int port) throws java.io.IOException {
        try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", port), 400); return true; }
        catch (java.net.ConnectException e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof android.system.ErrnoException
                    && ((android.system.ErrnoException) cause).errno == OsConstants.ECONNREFUSED) return false;
                cause = cause.getCause();
            }
            throw new java.io.IOException("无法确认本机端口状态：" + port, e);
        }
    }
    private static String read(String path, int limit) throws Exception {
        try (InputStream stream = new FileInputStream(path)) { return new String(Inspection.read(stream, limit), StandardCharsets.UTF_8); }
    }
    private static String hash(String text) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    private static JSONObject commandResult(String... argv) throws Exception {
        Process p = new ProcessBuilder(argv).redirectErrorStream(true).start();
        try {
            if (!p.waitFor(3, TimeUnit.SECONDS)) throw new java.io.IOException("系统命令超时：" + argv[0]);
            String text;
            try (InputStream in = p.getInputStream()) { text = new String(Inspection.read(in, 8192), StandardCharsets.UTF_8).trim(); }
            return new JSONObject().put("exit", p.exitValue()).put("text", text);
        } finally { p.destroy(); }
    }
    private static String command(String... argv) throws Exception {
        JSONObject r = commandResult(argv);
        if (r.getInt("exit") != 0) throw new java.io.IOException("命令被拒绝：" + argv[0] + " " + r.getString("text"));
        return r.getString("text");
    }
    private static String prop(String key) throws Exception { return command("/system/bin/getprop", key); }
    private static void set(String key, String value) throws Exception {
        command("/system/bin/setprop", key, value);
        if (!prop(key).equals(value)) throw new java.io.IOException("属性未生效：" + key);
    }
    private static void createExclusive(File file, String text, int mode) throws Exception {
        java.io.FileDescriptor fd = Os.open(file.getPath(), OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL | OsConstants.O_NOFOLLOW, mode);
        // Android FileOutputStream(FileDescriptor) borrows the descriptor: close()
        // does not close it. A writable descriptor left open causes ETXTBSY on exec.
        try (FileOutputStream out = new FileOutputStream(fd)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } finally {
            Os.close(fd);
        }
    }
    private void save(String name, JSONObject value) throws Exception {
        File tmp = new File(context.getFilesDir(), name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(value.toString(2).getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        Os.rename(tmp.getPath(), new File(context.getFilesDir(), name).getPath());
    }
}
