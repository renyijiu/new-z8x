package local.projector.homeswitch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> actions = new ArrayList<>();
    private TextView current, message;
    private boolean pending;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 700); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(48), dp(20), dp(48), dp(20));
        layout.setBackgroundColor(Color.rgb(17, 27, 40));
        TextView title = text("极米桌面切换", 27);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); layout.addView(title);
        layout.addView(text("NEW Z8X · 1.0   |   安装后可独立切换，无需电脑", 14));
        current = text("读取当前默认桌面……", 18); layout.addView(current);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        button(row, "默认简洁桌面", () -> run("clean"));
        button(row, "默认极米桌面", () -> run("stock"));
        layout.addView(row);
        LinearLayout extras = new LinearLayout(this); extras.setOrientation(LinearLayout.HORIZONTAL);
        button(extras, "临时打开极米桌面", this::openStock);
        button(extras, "恢复检查", () -> run("recover"));
        layout.addView(extras);
        message = text(SwitchService.status, 16); layout.addView(message);
        layout.addView(text("“临时打开”不改变默认桌面，按 Home 回到默认桌面。\n切换期间请勿断电；完成后会自动关闭临时维护通道。", 13));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(layout); setContentView(scroll);
        if (!SwitchService.busy) SwitchService.status = getSharedPreferences("ui", 0).getString("last_status", "选择需要使用的桌面。");
        actions.get(0).requestFocus();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.rgb(226, 234, 244)); view.setPadding(0, dp(5), 0, dp(5));
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); return view;
    }
    private GradientDrawable background(int color, boolean focus) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(12));
        if (focus) d.setStroke(dp(3), Color.rgb(130, 211, 255)); return d;
    }
    private void button(LinearLayout row, String label, Runnable click) {
        Button b = new Button(this); b.setText(label); b.setTextSize(18); b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER); b.setMinHeight(dp(56));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, background(Color.rgb(39, 83, 113), true));
        states.addState(new int[]{android.R.attr.state_pressed}, background(Color.rgb(39, 83, 113), true));
        states.addState(new int[]{}, background(Color.rgb(36, 50, 67), false)); b.setBackground(states);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(60), 1);
        params.setMargins(dp(4), dp(6), dp(4), dp(6)); row.addView(b, params);
        b.setOnClickListener(v -> click.run()); actions.add(b);
    }
    private void run(String action) {
        if (SwitchService.busy) return;
        if (pending && !"recover".equals(action)) return;
        startForegroundService(new Intent(this, SwitchService.class).putExtra("action", action));
    }
    private void openStock() {
        if (SwitchService.busy || pending) return;
        try { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName.unflattenFromString(HomePlan.STOCK)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { SwitchService.status = "无法打开极米桌面：" + e.getMessage(); }
    }
    private void update() {
        try {
            String home = Inspection.inspect(this).optString("resolved_home");
            current.setText("当前默认：" + (HomePlan.CLEAN.equals(home) ? "简洁桌面" : HomePlan.STOCK.equals(home) ? "极米桌面" : "未确定（" + home + "）"));
            File journal = new File(getFilesDir(), "journal.json"); pending = false;
            if (journal.isFile()) try (FileInputStream in = new FileInputStream(journal)) {
                pending = new JSONObject(new String(Inspection.read(in, 32768), StandardCharsets.UTF_8)).optBoolean("pending", true);
            }
        } catch (Exception e) { pending = true; current.setText("当前状态读取失败，请先恢复检查"); }
        for (int i = 0; i < actions.size(); i++) {
            boolean enabled = !SwitchService.busy && (!pending || i == 3);
            actions.get(i).setEnabled(enabled); actions.get(i).setAlpha(enabled ? 1f : .45f);
        }
        message.setText(!SwitchService.busy && pending ? "上次操作清理尚未完成，请先点“恢复检查”。\n" + SwitchService.status : SwitchService.status);
    }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
