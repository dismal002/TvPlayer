package com.dismal.tvplayer.data;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the video library. MediaStore is the primary source (it also indexes
 * mounted USB drives); when it is empty we fall back to the same filesystem
 * walk VideoPlayer-main performed, using its video extension list.
 */
public final class MediaLibrary {

    private static final String TAG = "MediaLibrary";
    private static final int MAX_SCAN_DEPTH = 12;

    /** Video extensions used by VideoPlayer-main (values/arrays.xml). */
    private static final Set<String> EXTENSIONS = new HashSet<>(Arrays.asList(
            "avi", "rmvb", "mkv", "m2ts", "ts", "dat", "mp4", "mpeg", "vob", "wmv", "mov",
            "mpg", "m4v", "rm", "flv", "pmp", "asf", "psr", "3gp", "ram", "divx", "m4p",
            "m4b", "f4v", "3gpp", "3g2", "3gpp2", "webm", "tp", "3dv", "3dm"));

    private MediaLibrary() {
    }

    public static List<VideoItem> scan(Context context) {
        List<VideoItem> items = queryMediaStore(context);
        if (items.isEmpty()) {
            items = scanFiles();
        }
        return items;
    }

    private static List<VideoItem> queryMediaStore(Context context) {
        List<VideoItem> items = new ArrayList<>();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED
        };
        try (Cursor cursor = context.getContentResolver().query(
                collection, projection, null, null, null)) {
            if (cursor == null) {
                return items;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                String path = cursor.getString(dataColumn);
                if (path == null || path.isEmpty()) {
                    continue;
                }
                String fileName = cursor.getString(nameColumn);
                if (fileName == null) {
                    fileName = new File(path).getName();
                }
                long id = cursor.getLong(idColumn);
                Uri contentUri = ContentUris.withAppendedId(collection, id);
                items.add(new VideoItem(
                        id,
                        stripExtension(fileName),
                        path,
                        contentUri.toString(),
                        cursor.getLong(durationColumn),
                        cursor.getLong(sizeColumn),
                        cursor.getLong(dateColumn) * 1000L));
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaStore query failed", e);
        }
        return items;
    }

    private static List<VideoItem> scanFiles() {
        List<VideoItem> items = new ArrayList<>();
        File root = Environment.getExternalStorageDirectory();
        if (root != null && root.canRead()) {
            walk(root, items, 0);
        }
        return items;
    }

    private static void walk(File directory, List<VideoItem> out, int depth) {
        if (depth > MAX_SCAN_DEPTH) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isHidden()) {
                continue;
            }
            if (child.isDirectory()) {
                walk(child, out, depth + 1);
            } else if (isVideo(child.getName())) {
                out.add(new VideoItem(
                        -1L,
                        stripExtension(child.getName()),
                        child.getAbsolutePath(),
                        Uri.fromFile(child).toString(),
                        0L,
                        child.length(),
                        child.lastModified()));
            }
        }
    }

    private static boolean isVideo(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.US));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
