package local.projector.returndiag;
import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
final class ReportSender {
    static void ready(Context context) throws Exception { request(context, null); }
    static void send(Context context, JSONObject result) throws Exception { request(context, result); }
    private static void request(Context context, JSONObject result) throws Exception {
        JSONObject config;
        try (InputStream input = context.getAssets().open("receiver.json")) {
            config = new JSONObject(new String(Inspection.read(input, 4096), StandardCharsets.UTF_8));
        }
        HttpURLConnection connection = (HttpURLConnection) new URL("http://" + config.getString("host")
            + ":" + config.getInt("port") + (result == null ? "/ready" : "/result")).openConnection();
        try {
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(5000);
            connection.setRequestProperty("X-Report-Token", config.getString("token"));
            if (result != null) {
                byte[] bytes = result.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 1048576) throw new java.io.IOException("Report too large");
                connection.setRequestMethod("POST"); connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            if (connection.getResponseCode() != 200) throw new java.io.IOException("电脑接收服务未就绪，请回到电脑确认");
        } finally { connection.disconnect(); }
    }
}
