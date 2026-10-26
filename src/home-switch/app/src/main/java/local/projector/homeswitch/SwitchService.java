package local.projector.homeswitch;

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
        if (!"clean".equals(action) && !"stock".equals(action) && !"recover".equals(action)) {
            stopSelf(); return START_NOT_STICKY;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("switch", "桌面切换进度", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "switch")
            .setSmallIcon(R.drawable.icon).setContentTitle("极米桌面切换")
            .setContentText("正在切换并清理维护通道，请勿断电。")
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
                else if (result.optBoolean("recovery")) status = "恢复检查完成，临时维护通道已关闭。";
                else if (result.optBoolean("home_verified"))
                    status = "已设为默认：" + ("clean".equals(action) ? "简洁桌面" : "极米桌面") + "。\n维护通道已关闭。请按遥控器 Home 键测试。";
                else status = "未确认切换成功，维护通道已关闭。\n请查看上方当前默认桌面；可重试或切换回极米桌面。";
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
