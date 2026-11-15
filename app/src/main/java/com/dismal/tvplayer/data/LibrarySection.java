package com.dismal.tvplayer.data;

/** A sidebar entry that filters the video grid. */
public final class LibrarySection {

    public static final int ALL = 0;
    public static final int RECENT = 1;
    public static final int RECORDED = 2;
    public static final int FAVORITES = 3;
    public static final int FOLDER = 4;

    public final int type;
    public final String folder;

    public LibrarySection(int type, String folder) {
        this.type = type;
        this.folder = folder;
    }

    public String key() {
        return type + ":" + (folder == null ? "" : folder);
    }
}
