package com.dismal.tvplayer.ui;

import com.dismal.tvplayer.R;
import com.dismal.tvplayer.data.LibrarySection;

/** One row in the Leanback sidebar. */
public final class SidebarItem {

    public static final int KIND_HEADER = 0;
    public static final int KIND_SECTION = 1;
    public static final int KIND_ACTION = 2;

    public static final int ACTION_SORT = 0;
    public static final int ACTION_ORDER = 1;
    public static final int ACTION_RESCAN = 2;
    public static final int ACTION_ABOUT = 3;
    public static final int ACTION_EXIT = 4;
    public static final int ACTION_SETTINGS = 5;

    public final int kind;
    public String label;
    public final String count;
    public final int iconRes;
    public final LibrarySection section;
    public final int action;
    public boolean selected;

    private SidebarItem(int kind, String label, String count, int iconRes,
                        LibrarySection section, int action) {
        this.kind = kind;
        this.label = label;
        this.count = count;
        this.iconRes = iconRes;
        this.section = section;
        this.action = action;
    }

    public static SidebarItem header(String label) {
        return new SidebarItem(KIND_HEADER, label, null, 0, null, -1);
    }

    public static SidebarItem section(String label, int iconRes, LibrarySection section, int count) {
        return new SidebarItem(KIND_SECTION, label, String.valueOf(count), iconRes, section, -1);
    }

    public static SidebarItem action(String label, int iconRes, int action) {
        return new SidebarItem(KIND_ACTION, label, null, iconRes, null, action);
    }

    public static int iconForSection(LibrarySection section) {
        switch (section.type) {
            case LibrarySection.RECENT:
                return R.drawable.ic_recent;
            case LibrarySection.RECORDED:
                return R.drawable.ic_recorded;
            case LibrarySection.FAVORITES:
                return R.drawable.ic_star;
            case LibrarySection.FOLDER:
                return R.drawable.ic_folder;
            case LibrarySection.ALL:
            default:
                return R.drawable.ic_all_videos;
        }
    }
}
