package com.dismal.tvplayer.util;

import java.util.Locale;

public final class TimeUtils {

    private TimeUtils() {
    }

    /** Formats milliseconds as M:SS or H:MM:SS; unknown durations return "--:--". */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            return "--:--";
        }
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}
