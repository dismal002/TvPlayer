package com.dismal.tvplayer.data;

import java.io.File;
import java.util.Locale;

/**
 * One playable local video. Mirrors the columns VideoPlayer-main kept in its
 * LocalMediaProvider (path, duration, size, favorite, latest play time).
 */
public final class VideoItem {

    public final long id;
    public final String title;
    public final String path;
    public final String uri;
    public final String folder;
    public final long durationMs;
    public final long sizeBytes;
    public final long dateModified;

    /** Runtime state, refreshed from LibraryStore. */
    public boolean favorite;
    public long positionMs;
    public long lastPlayed;

    public VideoItem(long id, String title, String path, String uri, long durationMs,
                     long sizeBytes, long dateModified) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.uri = uri;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.dateModified = dateModified;
        File parent = new File(path).getParentFile();
        this.folder = parent != null ? parent.getAbsolutePath() : "/";
    }

    public String folderName() {
        int index = folder.lastIndexOf('/');
        if (index >= 0 && index < folder.length() - 1) {
            return folder.substring(index + 1);
        }
        return folder;
    }

    public String sizeText() {
        if (sizeBytes <= 0) {
            return "";
        }
        double kb = sizeBytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.0f MB", mb);
        }
        return String.format(Locale.US, "%.1f GB", mb / 1024.0);
    }

    /** Watch progress in per-mille, matching the thin progress bar on the cards. */
    public int progressPermille() {
        if (positionMs <= 0 || durationMs <= 0) {
            return 0;
        }
        long value = Math.min(1000L, positionMs * 1000L / durationMs);
        return (int) value;
    }
}
