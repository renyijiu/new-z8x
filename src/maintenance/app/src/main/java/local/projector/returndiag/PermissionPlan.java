package local.projector.returndiag;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
final class PermissionPlan {
    private static final String PKG = "com.gathin.tvhome";
    private static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
    static String prepare(Context c, String action) {
        SharedPreferences p = c.getSharedPreferences("return_permission", 0);
        if (!p.contains("saved")) {
            if ("revoke".equals(action)) throw new IllegalStateException("没有本工具的授权记录，未更改");
            boolean before = c.getPackageManager().checkPermission(PERMISSION, PKG) == PackageManager.PERMISSION_GRANTED;
            if (!p.edit().putBoolean("saved", true).putBoolean("granted_before", before).commit())
                throw new IllegalStateException("权限原状态保存失败");
        }
        if ("revoke".equals(action) && p.getBoolean("granted_before", true))
            return "echo 'permission=originally-granted-no-change'\n";
        return "/system/bin/pm " + ("revoke".equals(action) ? "revoke" : "grant") + " " + PKG + " " + PERMISSION
            + "\npermission_rc=$?\nprintf 'permission_rc=%s\\n' \"$permission_rc\"\n[ \"$permission_rc\" = 0 ] || exit 55\n";
    }
}
