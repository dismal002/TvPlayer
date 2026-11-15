package com.dismal.tvplayer.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Persists resume points, favorites and last-played timestamps.
 * This is the SharedPreferences equivalent of the playtime/favorite bookkeeping
 * in VideoPlayer-main (AppConfig RECORDED_PLAYTIME / RECORDED_FAVORITE).
 */
public final class LibraryStore {

    private static final String PREFS = "library_store";
    private static final String KEY_FAVORITES = "favorites";
    private static final String PREFIX_POSITION = "position:";
    private static final String PREFIX_PLAYED = "played:";

    private static LibraryStore sInstance;

    private final SharedPreferences prefs;

    public static synchronized LibraryStore get(Context context) {
        if (sInstance == null) {
            sInstance = new LibraryStore(context.getApplicationContext());
        }
        return sInstance;
    }

    private LibraryStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public long getPosition(String path) {
        return prefs.getLong(PREFIX_POSITION + path, 0L);
    }

    public void setPosition(String path, long positionMs) {
        if (positionMs <= 0) {
            prefs.edit().remove(PREFIX_POSITION + path).apply();
        } else {
            prefs.edit().putLong(PREFIX_POSITION + path, positionMs).apply();
        }
    }

    public long getLastPlayed(String path) {
        return prefs.getLong(PREFIX_PLAYED + path, 0L);
    }

    public void markPlayed(String path) {
        prefs.edit().putLong(PREFIX_PLAYED + path, System.currentTimeMillis()).apply();
    }

    public boolean isFavorite(String path) {
        return favorites().contains(path);
    }

    public boolean toggleFavorite(String path) {
        Set<String> favorites = favorites();
        boolean added;
        if (favorites.contains(path)) {
            favorites.remove(path);
            added = false;
        } else {
            favorites.add(path);
            added = true;
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
        return added;
    }

    public int favoriteCount() {
        return favorites().size();
    }

    private Set<String> favorites() {
        Set<String> stored = prefs.getStringSet(KEY_FAVORITES, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }
}
