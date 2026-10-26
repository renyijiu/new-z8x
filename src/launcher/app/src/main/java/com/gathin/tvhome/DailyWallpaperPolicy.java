package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/** Bounded requests and cache timing, independent of Android for regression tests. */
final class DailyWallpaperPolicy {
    static final String ENDPOINT = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN";
    static final long RETRY_MS = 60 * 60 * 1000L;
    static final int IMAGE_LIMIT = 2 * 1024 * 1024;

    private DailyWallpaperPolicy() {}

    static boolean due(String today, String successDay, long now, long lastAttempt, boolean cached) {
        if (cached && today.equals(successDay)) return false;
        return lastAttempt == 0 || now < lastAttempt || now - lastAttempt >= RETRY_MS;
    }

    /** Candidate metadata only. Never choose an image the provider marks unavailable for wallpaper. */
    static final class Candidate {
        final String url, date, credit, title;
        final boolean eligible;
        Candidate(String url, String date, String credit, String title, boolean eligible) {
            this.url = url; this.date = date; this.credit = credit; this.title = title; this.eligible = eligible;
        }
    }

    static Candidate choose(java.util.List<Candidate> candidates, String currentUrl) throws IOException {
        Candidate first = null;
        for (Candidate candidate : candidates) {
            if (!candidate.eligible) continue;
            if (first == null) first = candidate;
            // When today's photo is unavailable or the feed has not rolled over,
            // choose a different eligible recent photo instead of freezing yesterday's cache.
            if (!candidate.url.equals(currentUrl)) return candidate;
        }
        if (first != null) return first;
        throw new IOException("No eligible wallpaper in the recent archive");
    }

    static String sourceDate(String archiveDate) {
        try {
            return java.time.LocalDate.parse(archiveDate, java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toString();
        } catch (java.time.DateTimeException e) {
            return "";
        }
    }

    static URI checkedUri(String value) throws IOException {
        try {
            URI uri = new URI(value);
            // These two exact hosts serve the archive and its images. Never downgrade HTTPS or follow
            // redirects to arbitrary hosts supplied in a response.
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !("www.bing.com".equalsIgnoreCase(host) || "cn.bing.com".equalsIgnoreCase(host))) {
                throw new IOException("Unsupported wallpaper URL");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IOException("Invalid wallpaper URL", e);
        }
    }

    static String imageUrl(String path) throws IOException {
        try {
            return checkedUri(URI.create(ENDPOINT).resolve(path).toString()).toString();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid image path", e);
        }
    }

    static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            if (count > limit - output.size()) throw new IOException("Wallpaper response too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
