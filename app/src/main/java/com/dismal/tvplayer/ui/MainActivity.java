package com.dismal.tvplayer.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.dismal.tvplayer.R;
import com.dismal.tvplayer.data.AppConfig;
import com.dismal.tvplayer.data.LibrarySection;
import com.dismal.tvplayer.data.LibraryStore;
import com.dismal.tvplayer.data.MediaLibrary;
import com.dismal.tvplayer.data.VideoItem;
import com.dismal.tvplayer.player.PlayerActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browse screen: a Leanback sidebar with the VideoPlayer-main library sections,
 * folders and options on the left, and a Leanback vertical grid of files on the
 * right.
 */
public class MainActivity extends FragmentActivity {

    private static final int REQUEST_SETTINGS = 102;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<VideoItem> videos = new ArrayList<>();

    private AppConfig config;
    private LibraryStore store;

    private View sidebarPane;
    private VerticalGridView sidebar;
    private ArrayObjectAdapter sidebarAdapter;
    private ItemBridgeAdapter sidebarBridge;

    private VideoGridFragment gridFragment;

    private View loadingPanel;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyHint;
    private TextView sectionTitle;

    private LibrarySection section = new LibrarySection(LibrarySection.ALL, null);
    private boolean sidebarEventsSuppressed;
    private boolean firstLoadDone;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = AppConfig.get(this);
        store = LibraryStore.get(this);

        sidebarPane = findViewById(R.id.sidebar_pane);
        sidebar = findViewById(R.id.sidebar);
        findViewById(R.id.search_orb).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SearchActivity.class)));
        loadingPanel = findViewById(R.id.loading_panel);
        emptyState = findViewById(R.id.empty_state);
        emptyTitle = findViewById(R.id.empty_title);
        emptyHint = findViewById(R.id.empty_hint);
        sectionTitle = findViewById(R.id.section_title);

        setupSidebar();
        setupGrid();

        if (!hasStoragePermission()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        reloadLibrary();
    }

    private void setupSidebar() {
        sidebarAdapter = new ArrayObjectAdapter(
                new SidebarPresenter(this::onSidebarItemClicked));
        sidebarBridge = new ItemBridgeAdapter();
        sidebarBridge.setAdapter(sidebarAdapter);
        sidebar.setAdapter(sidebarBridge);

        sidebar.setOnChildViewHolderSelectedListener(
                new OnChildViewHolderSelectedListener() {
                    @Override
                    public void onChildViewHolderSelected(@NonNull RecyclerView parent,
                                                          RecyclerView.ViewHolder child,
                                                          int position, int subposition) {
                        if (sidebarEventsSuppressed || position < 0
                                || position >= sidebarAdapter.size()) {
                            return;
                        }
                        Object object = sidebarAdapter.get(position);
                        if (object instanceof SidebarItem) {
                            SidebarItem item = (SidebarItem) object;
                            if (item.kind == SidebarItem.KIND_SECTION
                                    && !item.section.key().equals(section.key())) {
                                showSection(item.section, false);
                            }
                        }
                    }
                });

        final View searchOrb = findViewById(R.id.search_orb);
        searchOrb.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                sidebar.requestFocus();
                return true;
            }
            return false;
        });

        sidebar.setOnKeyInterceptListener(event -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                gridFragment.requestGridFocus();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && isFirstSidebarRowFocused()) {
                searchOrb.requestFocus();
                return true;
            }
            return false;
        });
    }

    private boolean isFirstSidebarRowFocused() {
        View focused = sidebar.getFocusedChild();
        if (focused == null) {
            return false;
        }
        RecyclerView.ViewHolder holder = sidebar.getChildViewHolder(focused);
        return holder != null && holder.getAdapterPosition() == 0;
    }

    private void setupGrid() {
        gridFragment = new VideoGridFragment();
        gridFragment.setListener(new VideoGridFragment.Listener() {
            @Override
            public void onVideoClicked(List<VideoItem> items, int index) {
                openPlayer(items, index);
            }

            @Override
            public void onFavoriteToggled(VideoItem item) {
                toggleFavorite(item);
            }
        });
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.grid_container, gridFragment)
                .commitNow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasStoragePermission()) {
            return;
        }
        if (firstLoadDone) {
            refreshFromStore();
        } else if (!loading) {
            reloadLibrary();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK && data != null
                && data.getBooleanExtra(SettingsActivity.EXTRA_RESCAN, false)) {
            reloadLibrary();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!sidebar.hasFocus()) {
            sidebar.requestFocus();
            return;
        }
        super.onBackPressed();
    }

    private boolean hasStoragePermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void reloadLibrary() {
        if (loading) {
            return;
        }
        loading = true;
        loadingPanel.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<VideoItem> loaded = MediaLibrary.scan(getApplicationContext());
            for (VideoItem video : loaded) {
                video.favorite = store.isFavorite(video.path);
                video.positionMs = store.getPosition(video.path);
                video.lastPlayed = store.getLastPlayed(video.path);
            }
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                videos.clear();
                videos.addAll(loaded);
                loading = false;
                loadingPanel.setVisibility(View.GONE);
                firstLoadDone = true;
                rebuildSidebar();
                showSection(section, false);
                if (sidebar.getSelectedPosition() < 0) {
                    sidebar.requestFocus();
                }
            });
        });
    }

    private void refreshFromStore() {
        for (VideoItem video : videos) {
            video.favorite = store.isFavorite(video.path);
            video.positionMs = store.getPosition(video.path);
            video.lastPlayed = store.getLastPlayed(video.path);
        }
        rebuildSidebar();
        refreshContent();
    }

    private void onSidebarItemClicked(SidebarItem item) {
        if (item.kind == SidebarItem.KIND_SECTION) {
            showSection(item.section, true);
        } else if (item.kind == SidebarItem.KIND_ACTION) {
            performAction(item.action);
        }
    }

    private void performAction(int action) {
        switch (action) {
            case SidebarItem.ACTION_SORT: {
                int mode = config.getSortMode();
                if (mode == AppConfig.SORT_NAME) {
                    mode = AppConfig.SORT_SIZE;
                } else if (mode == AppConfig.SORT_SIZE) {
                    mode = AppConfig.SORT_DATE;
                } else {
                    mode = AppConfig.SORT_NAME;
                }
                config.setSortMode(mode);
                updateActionLabels();
                refreshContent();
                break;
            }
            case SidebarItem.ACTION_ORDER:
                config.setAscending(!config.isAscending());
                updateActionLabels();
                refreshContent();
                break;
            case SidebarItem.ACTION_RESCAN:
                reloadLibrary();
                break;
            case SidebarItem.ACTION_ABOUT:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.about)
                        .setMessage(R.string.about_message)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show();
                break;
            case SidebarItem.ACTION_EXIT:
                finishAndRemoveTask();
                break;
            case SidebarItem.ACTION_SETTINGS:
                openSettings();
                break;
            default:
                break;
        }
    }

    private void openSettings() {
        startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS);
    }

    private void rebuildSidebar() {
        if (section.type == LibrarySection.FOLDER && !folderPaths().contains(section.folder)) {
            section = new LibrarySection(LibrarySection.ALL, null);
        }

        List<SidebarItem> items = new ArrayList<>();
        items.add(SidebarItem.section(getString(R.string.all_videos),
                R.drawable.ic_all_videos,
                new LibrarySection(LibrarySection.ALL, null),
                countFor(LibrarySection.ALL, null)));
        items.add(SidebarItem.section(getString(R.string.recent_played),
                R.drawable.ic_recent,
                new LibrarySection(LibrarySection.RECENT, null),
                countFor(LibrarySection.RECENT, null)));
        items.add(SidebarItem.section(getString(R.string.recorded_videos),
                R.drawable.ic_recorded,
                new LibrarySection(LibrarySection.RECORDED, null),
                countFor(LibrarySection.RECORDED, null)));
        items.add(SidebarItem.section(getString(R.string.my_favorite),
                R.drawable.ic_star,
                new LibrarySection(LibrarySection.FAVORITES, null),
                countFor(LibrarySection.FAVORITES, null)));

        List<String> folders = folderPaths();
        if (!folders.isEmpty()) {
            for (String folder : folders) {
                items.add(SidebarItem.section(folderLabel(folder),
                        R.drawable.ic_folder,
                        new LibrarySection(LibrarySection.FOLDER, folder),
                        countFor(LibrarySection.FOLDER, folder)));
            }
        }

        items.add(SidebarItem.action(getString(R.string.settings),
                R.drawable.ic_settings, SidebarItem.ACTION_SETTINGS));

        int selectedIndex = 1;
        for (int i = 0; i < items.size(); i++) {
            SidebarItem candidate = items.get(i);
            if (candidate.kind == SidebarItem.KIND_SECTION
                    && candidate.section.key().equals(section.key())) {
                selectedIndex = i;
                candidate.selected = true;
            }
        }

        int focusedPosition = -1;
        View focusedChild = sidebar.getFocusedChild();
        if (focusedChild != null) {
            RecyclerView.ViewHolder holder = sidebar.getChildViewHolder(focusedChild);
            if (holder != null) {
                focusedPosition = holder.getAdapterPosition();
            }
        }
        boolean restoreFocus = sidebar.hasFocus() && focusedPosition >= 0
                && focusedPosition < items.size();

        sidebarEventsSuppressed = true;
        sidebarAdapter.clear();
        sidebarAdapter.addAll(0, items);
        sidebar.setSelectedPosition(restoreFocus ? focusedPosition : selectedIndex);
        sidebar.post(() -> sidebarEventsSuppressed = false);
    }

    private void updateActionLabels() {
        for (int i = 0; i < sidebarAdapter.size(); i++) {
            Object object = sidebarAdapter.get(i);
            if (!(object instanceof SidebarItem)) {
                continue;
            }
            SidebarItem item = (SidebarItem) object;
            if (item.action == SidebarItem.ACTION_SORT) {
                item.label = sortLabel();
                sidebarAdapter.notifyItemRangeChanged(i, 1);
            } else if (item.action == SidebarItem.ACTION_ORDER) {
                item.label = orderLabel();
                sidebarAdapter.notifyItemRangeChanged(i, 1);
            }
        }
    }

    private String sortLabel() {
        switch (config.getSortMode()) {
            case AppConfig.SORT_SIZE:
                return getString(R.string.sort_by_size);
            case AppConfig.SORT_DATE:
                return getString(R.string.sort_by_date);
            case AppConfig.SORT_NAME:
            default:
                return getString(R.string.sort_by_name);
        }
    }

    private String orderLabel() {
        return getString(config.isAscending()
                ? R.string.order_ascending
                : R.string.order_descending);
    }

    private void showSection(LibrarySection newSection, boolean moveFocus) {
        section = newSection;
        markSidebarSelection();
        List<VideoItem> filtered = filter(newSection);
        String title = titleFor(newSection, filtered.size());
        sectionTitle.setText(title);
        gridFragment.setContent(title, filtered);
        updateEmptyState(filtered.isEmpty(), false);
        if (moveFocus) {
            gridFragment.requestGridFocus();
        }
    }

    private void refreshContent() {
        List<VideoItem> filtered = filter(section);
        String title = titleFor(section, filtered.size());
        sectionTitle.setText(title);
        if (gridFragment.sameItems(filtered)) {
            gridFragment.updateItems(filtered);
        } else {
            gridFragment.setContent(title, filtered);
        }
        updateEmptyState(filtered.isEmpty(), false);
    }

    private void updateEmptyState(boolean empty, boolean permissionMissing) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty && !permissionMissing) {
            emptyTitle.setText(R.string.empty_library);
            emptyHint.setText(R.string.empty_library_hint);
        }
    }

    private void markSidebarSelection() {
        if (sidebar.isComputingLayout()) {
            sidebar.post(this::markSidebarSelection);
            return;
        }
        for (int i = 0; i < sidebarAdapter.size(); i++) {
            Object object = sidebarAdapter.get(i);
            if (object instanceof SidebarItem) {
                SidebarItem item = (SidebarItem) object;
                item.selected = item.kind == SidebarItem.KIND_SECTION
                        && item.section.key().equals(section.key());
            }
        }
        if (sidebarAdapter.size() > 0) {
            sidebarAdapter.notifyItemRangeChanged(0, sidebarAdapter.size());
        }
    }

    private List<VideoItem> filter(LibrarySection target) {
        List<VideoItem> result = new ArrayList<>();
        for (VideoItem video : videos) {
            switch (target.type) {
                case LibrarySection.RECENT:
                    if (video.lastPlayed > 0) {
                        result.add(video);
                    }
                    break;
                case LibrarySection.RECORDED:
                    if (video.folder.toLowerCase(Locale.US).contains("dcim/camera")) {
                        result.add(video);
                    }
                    break;
                case LibrarySection.FAVORITES:
                    if (video.favorite) {
                        result.add(video);
                    }
                    break;
                case LibrarySection.FOLDER:
                    if (video.folder.equals(target.folder)) {
                        result.add(video);
                    }
                    break;
                case LibrarySection.ALL:
                default:
                    result.add(video);
                    break;
            }
        }

        if (target.type == LibrarySection.RECENT) {
            Collections.sort(result, (a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        } else {
            Collections.sort(result, comparator());
        }
        return result;
    }

    private Comparator<VideoItem> comparator() {
        Comparator<VideoItem> comparator;
        switch (config.getSortMode()) {
            case AppConfig.SORT_SIZE:
                comparator = (a, b) -> Long.compare(a.sizeBytes, b.sizeBytes);
                break;
            case AppConfig.SORT_DATE:
                comparator = (a, b) -> Long.compare(a.dateModified, b.dateModified);
                break;
            case AppConfig.SORT_NAME:
            default:
                comparator = (a, b) -> a.title.compareToIgnoreCase(b.title);
                break;
        }
        return config.isAscending() ? comparator : comparator.reversed();
    }

    private int countFor(int type, String folder) {
        int count = 0;
        for (VideoItem video : videos) {
            switch (type) {
                case LibrarySection.RECENT:
                    count += video.lastPlayed > 0 ? 1 : 0;
                    break;
                case LibrarySection.RECORDED:
                    count += video.folder.toLowerCase(Locale.US).contains("dcim/camera") ? 1 : 0;
                    break;
                case LibrarySection.FAVORITES:
                    count += video.favorite ? 1 : 0;
                    break;
                case LibrarySection.FOLDER:
                    count += video.folder.equals(folder) ? 1 : 0;
                    break;
                case LibrarySection.ALL:
                default:
                    count++;
                    break;
            }
        }
        return count;
    }

    private List<String> folderPaths() {
        TreeSet<String> folders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (VideoItem video : videos) {
            folders.add(video.folder);
        }
        return new ArrayList<>(folders);
    }

    private String folderLabel(String folder) {
        String name = new File(folder).getName();
        return name.isEmpty() ? folder : name;
    }

    private String titleFor(LibrarySection target, int count) {
        String label;
        switch (target.type) {
            case LibrarySection.RECENT:
                label = getString(R.string.recent_played);
                break;
            case LibrarySection.RECORDED:
                label = getString(R.string.recorded_videos);
                break;
            case LibrarySection.FAVORITES:
                label = getString(R.string.my_favorite);
                break;
            case LibrarySection.FOLDER:
                label = folderLabel(target.folder);
                break;
            case LibrarySection.ALL:
            default:
                label = getString(R.string.all_videos);
                break;
        }
        return label;
    }

    private void openPlayer(List<VideoItem> items, int index) {
        if (items.isEmpty()) {
            return;
        }
        String[] paths = new String[items.size()];
        String[] titles = new String[items.size()];
        String[] uris = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            VideoItem video = items.get(i);
            paths[i] = video.path;
            titles[i] = video.title;
            uris[i] = video.uri;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_PATHS, paths);
        intent.putExtra(PlayerActivity.EXTRA_TITLES, titles);
        intent.putExtra(PlayerActivity.EXTRA_URIS, uris);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index);
        startActivity(intent);
    }

    private void toggleFavorite(VideoItem video) {
        boolean added = store.toggleFavorite(video.path);
        video.favorite = added;
        Toast.makeText(this, added ? R.string.favorite_added : R.string.favorite_removed,
                Toast.LENGTH_SHORT).show();
        rebuildSidebar();
        refreshContent();
    }

}
