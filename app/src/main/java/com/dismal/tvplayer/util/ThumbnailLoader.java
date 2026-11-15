package com.dismal.tvplayer.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import com.dismal.tvplayer.R;
import com.dismal.tvplayer.data.VideoItem;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads and caches video thumbnails off the main thread. The cache and worker
 * approach follows VideoPlayer-main's ThumbsCache/ThumbsFetcher design, using
 * MediaStore thumbnails where possible.
 */
public final class ThumbnailLoader {

    private static final int CACHE_ENTRIES = 80;
    private static final Size TARGET_SIZE = new Size(480, 270);

    private final Context context;
    private final LruCache<String, Bitmap> cache = new LruCache<>(CACHE_ENTRIES);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ThumbnailLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public void load(VideoItem item, ImageView view) {
        final String key = item.path;
        view.setTag(R.id.tag_thumbnail_key, key);

        Bitmap cached = cache.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        view.setImageResource(R.drawable.ic_movie);
        executor.execute(() -> {
            Bitmap bitmap = decode(item);
            if (bitmap == null) {
                return;
            }
            cache.put(key, bitmap);
            mainHandler.post(() -> {
                if (key.equals(view.getTag(R.id.tag_thumbnail_key))) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    public void release(ImageView view) {
        view.setTag(R.id.tag_thumbnail_key, null);
    }

    private Bitmap decode(VideoItem item) {
        ContentResolver resolver = context.getContentResolver();
        try {
            if (item.id >= 0) {
                Uri uri = Uri.parse(item.uri);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return resolver.loadThumbnail(uri, TARGET_SIZE, null);
                }
                return MediaStore.Video.Thumbnails.getThumbnail(
                        resolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return android.media.ThumbnailUtils.createVideoThumbnail(
                        new File(item.path), TARGET_SIZE, null);
            }
            return android.media.ThumbnailUtils.createVideoThumbnail(
                    item.path, MediaStore.Video.Thumbnails.MINI_KIND);
        } catch (Exception e) {
            return null;
        }
    }
}
