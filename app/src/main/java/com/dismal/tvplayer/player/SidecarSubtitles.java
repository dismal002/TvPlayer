package com.dismal.tvplayer.player;

import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds subtitle files stored next to a video (movie.srt, movie.en.srt,
 * movie.forced.ass ...), the same convention VideoPlayer-main used when it
 * looked for external subtitle tracks.
 */
public final class SidecarSubtitles {

    private SidecarSubtitles() {
    }

    public static List<MediaItem.SubtitleConfiguration> find(String videoPath) {
        List<MediaItem.SubtitleConfiguration> subtitles = new ArrayList<>();
        if (videoPath == null) {
            return subtitles;
        }
        File video = new File(videoPath);
        File folder = video.getParentFile();
        if (folder == null) {
            return subtitles;
        }
        String baseName = stripExtension(video.getName());
        if (baseName.isEmpty()) {
            return subtitles;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return subtitles;
        }

        String lowerBase = baseName.toLowerCase(Locale.US);
        for (File file : files) {
            if (file.isDirectory() || file.isHidden()) {
                continue;
            }
            String name = file.getName();
            String extension = extension(name);
            String mimeType = mimeType(extension);
            if (mimeType == null) {
                continue;
            }
            String candidate = stripExtension(name);
            String lowerCandidate = candidate.toLowerCase(Locale.US);
            if (!lowerCandidate.startsWith(lowerBase)) {
                continue;
            }
            String remainder = lowerCandidate.substring(lowerBase.length());
            if (!remainder.isEmpty() && remainder.charAt(0) != '.') {
                continue;
            }
            String language = remainder.length() > 1 ? remainder.substring(1) : "und";
            subtitles.add(new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                    .setMimeType(mimeType)
                    .setLanguage(language)
                    .setLabel(name)
                    .build());
        }

        Collections.sort(subtitles, new Comparator<MediaItem.SubtitleConfiguration>() {
            @Override
            public int compare(MediaItem.SubtitleConfiguration first,
                               MediaItem.SubtitleConfiguration second) {
                String left = first.label == null ? "" : first.label;
                String right = second.label == null ? "" : second.label;
                return left.compareToIgnoreCase(right);
            }
        });
        return subtitles;
    }

    private static String mimeType(String extension) {
        switch (extension) {
            case "srt":
                return MimeTypes.APPLICATION_SUBRIP;
            case "vtt":
                return MimeTypes.TEXT_VTT;
            case "ssa":
            case "ass":
                return MimeTypes.TEXT_SSA;
            default:
                return null;
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
