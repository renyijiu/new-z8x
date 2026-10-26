package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.AtomicFile;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One daily photo, fetched only while Home is visible; no Android Service, alarm, or boot receiver. */
final class DailyWallpaper {
    static final String PREFS = "daily_wallpaper";
    private final SharedPreferences preferences;
    private final AtomicFile cache;
    private final ImageView image;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Bitmap shown;
    private long shownStamp;
    private boolean active;
    private boolean loading;
    private volatile boolean closed;

    DailyWallpaper(Context context, FrameLayout root) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getInt("selection_version", 0) < 2) {
            preferences.edit().putInt("selection_version", 2).remove("day").putLong("attempt", 0).apply();
        }
        cache = new AtomicFile(new File(context.getFilesDir(), "daily-wallpaper.jpg"));
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(image, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void start() {
        active = true;
        update();
    }

    void stop() { active = false; }

    void close() {
        active = false;
        closed = true;
        worker.shutdownNow();
        image.setImageDrawable(null);
        shown = null; // Let Android release any bitmap still referenced by a rendered frame.
    }

    void update() {
        if (!active || closed || loading) return;
        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        boolean hasCache = cache.getBaseFile().isFile();
        boolean fetch = DailyWallpaperPolicy.due(today, preferences.getString("day", ""), now,
                preferences.getLong("attempt", 0), hasCache);
        long cacheStamp = cache.getBaseFile().lastModified();
        if (!fetch && ((shown != null && shownStamp == cacheStamp) || !hasCache)) return;
        loading = true;
        if (fetch) preferences.edit().putLong("attempt", now).apply();
        boolean needsCachedImage = hasCache && (shown == null || shownStamp != cacheStamp);
        worker.execute(() -> {
            try {
                if (needsCachedImage) {
                    try (InputStream input = cache.openRead()) {
                        deliver(decode(DailyWallpaperPolicy.readBounded(input, DailyWallpaperPolicy.IMAGE_LIMIT)), cacheStamp);
                    } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
                        cache.delete(); // A corrupt cache must not prevent a fresh download.
                    }
                }
                if (fetch && !closed) download(today);
            } catch (Exception | OutOfMemoryError failure) {
                if (!closed) preferences.edit().putBoolean("failed", true).apply();
            } finally {
                image.post(() -> loading = false);
            }
        });
    }

    private void download(String today) throws Exception {
        byte[] json = fetch(DailyWallpaperPolicy.ENDPOINT, 64 * 1024);
        org.json.JSONArray entries = new JSONObject(new String(json, StandardCharsets.UTF_8)).getJSONArray("images");
        java.util.List<DailyWallpaperPolicy.Candidate> candidates = new java.util.ArrayList<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            if (!entry.optBoolean("wp", false)) continue;
            try {
                candidates.add(new DailyWallpaperPolicy.Candidate(
                        DailyWallpaperPolicy.imageUrl(entry.getString("url")),
                        DailyWallpaperPolicy.sourceDate(entry.optString("enddate", "")),
                        entry.optString("copyright", "Bing"), entry.optString("title", ""), true));
            } catch (IOException | org.json.JSONException ignored) {
                // A malformed entry must not prevent trying a later valid wallpaper.
            }
        }
        DailyWallpaperPolicy.Candidate selected = DailyWallpaperPolicy.choose(candidates, preferences.getString("url", ""));
        String url = selected.url;
        if (!url.equals(preferences.getString("url", "")) || !cache.getBaseFile().isFile()) {
            byte[] data = fetch(url, DailyWallpaperPolicy.IMAGE_LIMIT);
            Bitmap bitmap = decode(data); // Validate before replacing the previous good image.
            if (closed) { bitmap.recycle(); return; }
            FileOutputStream output = null;
            try {
                output = cache.startWrite();
                output.write(data);
                cache.finishWrite(output);
                output = null;
            } catch (IOException e) {
                cache.failWrite(output);
                bitmap.recycle();
                throw e;
            }
            deliver(bitmap, cache.getBaseFile().lastModified());
        }
        if (!closed) preferences.edit().putString("day", today).putString("url", url)
                .putString("credit", selected.credit).putString("title", selected.title)
                .putString("image_day", selected.date).putBoolean("archive_fallback", !today.equals(selected.date))
                .putBoolean("failed", false).apply();
    }

    private byte[] fetch(String value, int limit) throws IOException {
        URI uri = DailyWallpaperPolicy.checkedUri(value);
        for (int redirect = 0; redirect <= 3; redirect++) {
            if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", "NewZ8XHome/2.6");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect");
                    uri = DailyWallpaperPolicy.checkedUri(uri.resolve(location).toString());
                    continue;
                }
                if (status != 200 || connection.getContentLengthLong() > limit) {
                    throw new IOException("Wallpaper request failed");
                }
                try (InputStream input = connection.getInputStream()) {
                    return DailyWallpaperPolicy.readBounded(input, limit);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private static Bitmap decode(byte[] data) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        int width = options.outWidth, height = options.outHeight;
        if (!WallpaperSize.accepted(width, height) || width > 4096 || height > 2160) throw new IOException("Invalid image");
        options.inSampleSize = WallpaperSize.sampleSize(width, height, 1920, 1080);
        int[] scale = WallpaperSize.coverScale(width / options.inSampleSize,
                height / options.inSampleSize, 1920, 1080);
        if (scale != null) {
            options.inScaled = true;
            options.inDensity = scale[0];
            options.inTargetDensity = scale[1];
        }
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) throw new IOException("Cannot decode image");
        new Canvas(bitmap).drawColor(0x30000000); // Keep white labels readable on bright landscapes.
        return bitmap;
    }

    private void deliver(Bitmap bitmap, long stamp) {
        image.post(() -> {
            if (closed || !active) { bitmap.recycle(); return; }
            Bitmap old = shown;
            shown = bitmap;
            shownStamp = stamp;
            image.setImageBitmap(bitmap);
            if (old != null) image.postDelayed(old::recycle, 500);
        });
    }
}
