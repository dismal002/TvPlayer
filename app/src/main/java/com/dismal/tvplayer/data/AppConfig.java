package com.dismal.tvplayer.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * User preferences: sort order, aspect handling and play mode. These are the
 * options that VideoPlayer-main exposed through AppConfig (sort flags, ZOOM,
 * PlayMode / repeat_mode_items).
 */
public final class AppConfig {

    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;

    public static final int ZOOM_FIT = 0;
    public static final int ZOOM_STRETCH = 1;
    public static final int ZOOM_CROP = 2;

    public static final int PLAY_ONCE = 0;
    public static final int PLAY_SEQUENCE = 1;
    public static final int PLAY_REPEAT_ALL = 2;
    public static final int PLAY_REPEAT_ONE = 3;
    public static final int PLAY_RANDOM = 4;

    private static final String PREFS = "app_config";
    private static final String KEY_SORT = "sort_mode";
    private static final String KEY_ASCENDING = "sort_ascending";
    private static final String KEY_ZOOM = "zoom_mode";
    private static final String KEY_PLAY_MODE = "play_mode";

    private static AppConfig sInstance;

    private final SharedPreferences prefs;

    public static synchronized AppConfig get(Context context) {
        if (sInstance == null) {
            sInstance = new AppConfig(context.getApplicationContext());
        }
        return sInstance;
    }

    private AppConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getSortMode() {
        return prefs.getInt(KEY_SORT, SORT_NAME);
    }

    public void setSortMode(int mode) {
        prefs.edit().putInt(KEY_SORT, mode).apply();
    }

    public boolean isAscending() {
        return prefs.getBoolean(KEY_ASCENDING, true);
    }

    public void setAscending(boolean ascending) {
        prefs.edit().putBoolean(KEY_ASCENDING, ascending).apply();
    }

    public int getZoomMode() {
        return prefs.getInt(KEY_ZOOM, ZOOM_FIT);
    }

    public void setZoomMode(int mode) {
        prefs.edit().putInt(KEY_ZOOM, mode).apply();
    }

    public int getPlayMode() {
        return prefs.getInt(KEY_PLAY_MODE, PLAY_SEQUENCE);
    }

    public void setPlayMode(int mode) {
        prefs.edit().putInt(KEY_PLAY_MODE, mode).apply();
    }
}
