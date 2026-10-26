package com.gathin.tvhome;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.junit.Assert.*;

public class DailyWallpaperPolicyTest {
    @Test public void currentCachedPhotoDoesNotFetchOnEveryHomeReturn() {
        assertFalse(DailyWallpaperPolicy.due("2026-10-23", "2026-10-23", 20_000_000L, 1L, true));
    }
    @Test public void nextDayRefreshesAndMissingCacheCanBeRecovered() {
        assertTrue(DailyWallpaperPolicy.due("2026-10-24", "2026-10-23", 20_000_000L, 1L, true));
        assertTrue(DailyWallpaperPolicy.due("2026-10-23", "2026-10-23", 20_000_000L, 1L, false));
    }
    @Test public void failedRequestsBackOffForAnHour() {
        assertFalse(DailyWallpaperPolicy.due("today", "", 1001L, 1000L, false));
        assertTrue(DailyWallpaperPolicy.due("today", "", 1000L + DailyWallpaperPolicy.RETRY_MS, 1000L, false));
    }
    @Test public void clockCorrectionDoesNotPermanentlyBlockUpdates() {
        assertTrue(DailyWallpaperPolicy.due("today", "yesterday", 1000L, 2000L, true));
    }
    @Test public void relativeBingImageUsesHttps() throws Exception {
        assertEquals("https://www.bing.com/th?id=OHR.Example_1920x1080.jpg",
                DailyWallpaperPolicy.imageUrl("/th?id=OHR.Example_1920x1080.jpg"));
        DailyWallpaperPolicy.checkedUri("https://cn.bing.com:443/th?id=sample");
    }
    @Test public void rejectsDowngradesLookalikeHostsAndCredentials() {
        String[] invalid = {"http://www.bing.com/a", "https://bing.com.evil.test/a", "https://evil.test/a",
                "file:///tmp/a", "https://user@www.bing.com/a", "https://www.bing.com:8443/a",
                "//www.bing.com/a", "https://www.bing.com.evil.test/a"};
        for (String url : invalid) {
            assertThrows(url, IOException.class, () -> DailyWallpaperPolicy.checkedUri(url));
        }
        assertThrows(IOException.class, () -> DailyWallpaperPolicy.imageUrl("//evil.test/a.jpg"));
        assertThrows(IOException.class, () -> DailyWallpaperPolicy.imageUrl("/invalid path"));
    }
    @Test public void permitsExactlyTheDownloadLimit() throws Exception {
        byte[] bytes = {1, 2, 3};
        assertArrayEquals(bytes, DailyWallpaperPolicy.readBounded(new ByteArrayInputStream(bytes), 3));
    }
    @Test public void rejectsOversizeResponsesEvenWithoutContentLength() {
        assertThrows(IOException.class, () -> DailyWallpaperPolicy.readBounded(
                new ByteArrayInputStream(new byte[8193]), 8192));
    }
    @Test public void interruptedDownloadStopsBeforeSaving() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> DailyWallpaperPolicy.readBounded(
                    new ByteArrayInputStream(new byte[1]), 2));
        } finally {
            Thread.interrupted();
        }
    }
    private DailyWallpaperPolicy.Candidate candidate(String url, boolean eligible) {
        return new DailyWallpaperPolicy.Candidate(url, "2026-10-24", "credit", "title", eligible);
    }
    @Test public void unavailableLatestImageFallsBackToDifferentEligiblePhoto() throws Exception {
        DailyWallpaperPolicy.Candidate pick = DailyWallpaperPolicy.choose(java.util.Arrays.asList(
                candidate("today", false), candidate("yesterday", true), candidate("earlier", true)), "yesterday");
        assertEquals("earlier", pick.url);
    }
    @Test public void availableNewImageWins() throws Exception {
        assertEquals("today", DailyWallpaperPolicy.choose(java.util.Arrays.asList(
                candidate("today", true), candidate("yesterday", true)), "yesterday").url);
    }
    @Test public void noEligibleImageDoesNotDiscardCache() {
        assertThrows(IOException.class, () -> DailyWallpaperPolicy.choose(
                java.util.Arrays.asList(candidate("unavailable", false)), "cached"));
    }
    @Test public void onlySameImageAvailableDoesNotChooseAnUnavailableOne() throws Exception {
        assertEquals("cached", DailyWallpaperPolicy.choose(java.util.Arrays.asList(
                candidate("unavailable", false), candidate("cached", true)), "cached").url);
    }
    @Test public void sourceDateIsSeparateFromDownloadDate() {
        assertEquals("2026-10-23", DailyWallpaperPolicy.sourceDate("20261023"));
        assertEquals("", DailyWallpaperPolicy.sourceDate("not-a-date"));
    }
}
