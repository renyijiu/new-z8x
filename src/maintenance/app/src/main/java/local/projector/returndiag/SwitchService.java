package local.projector.returndiag;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.IBinder;
import org.json.JSONObject;

/** A single foreground transaction survives navigation and Activity recreation. */
public final class SwitchService extends Service {
    static volatile boolean busy;
    static volatile String status = "选择需要使用的桌面。";
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (busy) return START_NOT_STICKY;
        String action = intent == null ? "" : intent.getStringExtra("action");
        if (!"pin".equals(action) && !"unpin".equals(action) && !"whitelist".equals(action) && !"unwhitelist".equals(action) && !"inspect".equals(action) && !"clean".equals(action) && !"trial".equals(action) && !"restore".equals(action) && !"recover".equals(action) && !"grant".equals(action) && !"revoke".equals(action) && !"adbopen".equals(action) && !"adbclose".equals(action)) {
            stopSelf(); return START_NOT_STICKY;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("switch", "桌面切换进度", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "switch")
            .setSmallIcon(R.drawable.icon).setContentTitle("极米返回路径诊断")
            .setContentText("只读检查后自动清理维护通道。")
            .setOngoing(true).build());
        busy = true;
        status = "正在检查兼容性……";
        new Thread(() -> {
            try {
                Probe probe = new Probe(getApplicationContext(), message -> status = message);
                JSONObject result = "recover".equals(action) ? probe.recover() : probe.run(action);
                JSONObject cleanup = result.optJSONObject("cleanup");
                boolean safe = cleanup != null && cleanup.optBoolean("verified");
                if (!safe) status = "清理尚未确认完成。请点“恢复检查”；若仍失败，请正常重启投影仪后再检查。";
                else status = (result.optString("home_log").contains("adb_open=key-installed") ? "本次 ADB 密钥已写入，请让电脑核对连接。\n" : result.optString("home_log").contains("adb_close=verified") ? "ADB 关闭和本次密钥移除已核对。\n" : "") + (result.optString("home_log").contains("permission_rc=0\n")
                    ? "权限命令执行成功，请回简洁桌面设置核对开关。\n"
                    : result.optString("home_log").contains("permission=originally-granted-no-change")
                        ? "权限在本工具运行前已存在，未撤销原有权限。\n"
                        : result.optString("home_log").contains("change=verified\n") ? "设置值已核对。\n" : "请让电脑核对操作结果。\n")
                    + (result.optBoolean("diagnostic_complete") ? "采集完整。" : "部分诊断数据未读取成功。")
                    + (result.optBoolean("sent") ? "诊断报告已发送到电脑。" : "报告未发送，请点击恢复检查／重发。")
                    + (("adbopen".equals(action) || "inspect".equals(action)) ? "\n临时 SSH 维护已关闭，ADB 留给电脑连接核对。" : "\n临时维护通道已关闭。")
                    + (result.has("send_error") ? "\n发送错误：" + result.optString("send_error") : "");

            } catch (Exception e) {
                status = "操作未完成：" + e.getMessage() + "\n如提示上次清理未完成，请点“恢复检查”。";
            } finally {
                getSharedPreferences("ui", 0).edit().putString("last_status", status).commit();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    stopForeground(true);
                    stopSelf(startId);
                    busy = false;
                });
            }
        }, "launcher-switch").start();
        return START_NOT_STICKY;
    }
}
