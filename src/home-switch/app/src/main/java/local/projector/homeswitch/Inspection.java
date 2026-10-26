package local.projector.homeswitch;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
final class Inspection {
    static JSONObject inspect(Context context) throws Exception {
        ResolveInfo home = context.getPackageManager().resolveActivity(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT), PackageManager.MATCH_DEFAULT_ONLY);
        return new JSONObject().put("resolved_home", home != null && home.activityInfo != null
            ? home.activityInfo.packageName + "/" + home.activityInfo.name : "unknown");
    }
    static byte[] read(InputStream stream, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while ((count = stream.read(buffer)) != -1) {
            if (out.size() + count > limit) throw new java.io.IOException("Read limit exceeded");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
