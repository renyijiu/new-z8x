package local.projector.returndiag;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** DUMP-protected entry point for fixed inspection, policy and session-close actions. */
public final class AdbCloseReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("local.projector.returndiag.PIN".equals(intent.getAction()) || "local.projector.returndiag.UNPIN".equals(intent.getAction())) {
            String action = "local.projector.returndiag.PIN".equals(intent.getAction()) ? "pin" : "unpin";
            context.startForegroundService(new Intent(context, SwitchService.class).putExtra("action", action));
            return;
        }
        if ("local.projector.returndiag.WHITELIST".equals(intent.getAction()) || "local.projector.returndiag.UNWHITELIST".equals(intent.getAction())) {
            String action = "local.projector.returndiag.WHITELIST".equals(intent.getAction()) ? "whitelist" : "unwhitelist";
            context.startForegroundService(new Intent(context, SwitchService.class).putExtra("action", action));
            return;
        }
        if ("local.projector.returndiag.INSPECT".equals(intent.getAction())) {
            context.startForegroundService(new Intent(context, SwitchService.class).putExtra("action", "inspect"));
            return;
        }
        if (!"local.projector.returndiag.CLOSE_ADB".equals(intent.getAction())) return;
        context.startForegroundService(new Intent(context, SwitchService.class).putExtra("action", "adbclose"));
    }
}
