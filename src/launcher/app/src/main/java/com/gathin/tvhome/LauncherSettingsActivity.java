package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private final android.os.Handler guardUi = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable guardRefresh = new Runnable() {
        @Override public void run() { refreshGuard(); guardUi.postDelayed(this, 1000); }
    };
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.stockHomeChoice).setOnClickListener(v -> {
                ReturnGuard.disarm(this);
                open(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName("com.xgimi.home", "com.xgimi.module.cellview.home.ui.HomeActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), R.string.stock_home_unavailable); });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v -> {
            if (!SystemSettings.open(this, Settings.ACTION_SETTINGS)) {
                Toast.makeText(this, R.string.projector_system_settings_help, Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.returnGuardChoice).setOnClickListener(v -> {
            if (ReturnGuard.enabled(this)) {
                try { ReturnGuard.setEnabled(this, false); } catch (RuntimeException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
                refreshGuard();
            } else new AlertDialog.Builder(this).setTitle(R.string.return_guard_name)
                .setMessage(R.string.return_guard_description)
                .setPositiveButton("开启", (dialog, which) -> {
                    try { ReturnGuard.setEnabled(this, true); } catch (RuntimeException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    refreshGuard();
                    guardUi.postDelayed(this::refreshGuard, 1500);
                }).setNegativeButton(android.R.string.cancel, null).show();
        });
        refreshGuard();
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void refreshGuard() {
        TextView row = findViewById(R.id.returnGuardChoice);
        ((TextView) findViewById(R.id.returnGuardTiming)).setText(ReturnGuard.timing(this));
        row.setText("应用返回兼容：" + (!ReturnGuard.enabled(this) ? "关闭" : ReturnGuardService.connected ? "已连接" : "开启，等待服务连接"));
    }
    @Override protected void onResume() { super.onResume(); guardUi.post(guardRefresh); }
    @Override protected void onPause() { guardUi.removeCallbacksAndMessages(null); super.onPause(); }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 3];
        labels[0] = getString(R.string.wallpaper_daily);
        labels[1] = getString(R.string.wallpaper_gradient);
        for (int i = 0; i < images.size(); i++) labels[i + 2] = images.get(i).getName();
        labels[labels.length - 1] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, Config.WALLPAPER_DAILY);
        int fileIndex = indexOf(images, current);
        int checked = Config.WALLPAPER_DAILY.equals(current) ? 0
                : Config.WALLPAPER_GRADIENT.equals(current) ? 1
                : "none".equals(current) ? labels.length - 1 : fileIndex < 0 ? -1 : fileIndex + 2;
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0 || which == 1) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                which == 0 ? Config.WALLPAPER_DAILY : Config.WALLPAPER_GRADIENT).apply();
                        if (which == 0) getSharedPreferences(DailyWallpaper.PREFS, MODE_PRIVATE)
                                .edit().putLong("attempt", 0).remove("day").apply();
                    } else if (which == labels.length - 1) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which - 2).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, Config.WALLPAPER_DAILY);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                Config.WALLPAPER_DAILY.equals(wallpaperCurrent) ? getString(R.string.wallpaper_daily)
                        : Config.WALLPAPER_GRADIENT.equals(wallpaperCurrent) ? getString(R.string.wallpaper_gradient)
                        : "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        TextView info = findViewById(R.id.wallpaperInfo);
        boolean daily = Config.WALLPAPER_DAILY.equals(wallpaperCurrent);
        info.setVisibility(daily ? View.VISIBLE : View.GONE);
        if (daily) {
            SharedPreferences status = getSharedPreferences(DailyWallpaper.PREFS, MODE_PRIVATE);
            String credit = status.getString("credit", "");
            info.setText(getString(R.string.wallpaper_daily_hint)
                    + (credit.isEmpty() ? "" : "\n" + getString(R.string.wallpaper_image_date, status.getString("image_day", "—")) + " · " + credit)
                    + (status.getBoolean("archive_fallback", false) ? "\n" + getString(R.string.wallpaper_archive_fallback) : "")
                    + (status.getBoolean("failed", false) ? "\n" + getString(R.string.wallpaper_daily_failed) : ""));
        }
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY);
    }

    private void open(Intent intent) {
        open(intent, R.string.not_available);
    }

    private void open(Intent intent, int failureMessage) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show();
        }
    }
}
