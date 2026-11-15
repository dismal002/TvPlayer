package com.dismal.tvplayer.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.VerticalGridSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.VerticalGridPresenter;

import com.dismal.tvplayer.R;
import com.dismal.tvplayer.data.VideoItem;
import com.dismal.tvplayer.util.ThumbnailLoader;

import java.util.ArrayList;
import java.util.List;

/** Leanback vertical grid that lists the videos of the selected section. */
public class VideoGridFragment extends VerticalGridSupportFragment {

    public interface Listener {
        void onVideoClicked(List<VideoItem> items, int index);

        void onFavoriteToggled(VideoItem item);
    }

    private ArrayObjectAdapter adapter;
    private final List<VideoItem> items = new ArrayList<>();
    private Listener listener;
    private ThumbnailLoader thumbnails;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        thumbnails = new ThumbnailLoader(requireContext());

        VerticalGridPresenter gridPresenter =
                new VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE);
        gridPresenter.setNumberOfColumns(getResources().getInteger(R.integer.grid_columns));
        gridPresenter.setShadowEnabled(false);
        setGridPresenter(gridPresenter);

        adapter = new ArrayObjectAdapter(new VideoCardPresenter(thumbnails,
                new VideoCardPresenter.Listener() {
                    @Override
                    public void onVideoClicked(VideoItem item) {
                        if (listener != null) {
                            int position = items.indexOf(item);
                            listener.onVideoClicked(new ArrayList<>(items),
                                    Math.max(0, position));
                        }
                    }

                    @Override
                    public void onFavoriteToggled(VideoItem item) {
                        if (listener != null) {
                            listener.onFavoriteToggled(item);
                        }
                    }
                }));
        setAdapter(adapter);
        setTitle("");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // The sidebar already labels the section, so drop Leanback's own title bar.
        int titleGroupId = getResources().getIdentifier(
                "browse_title_group", "id", "androidx.leanback");
        if (titleGroupId != 0) {
            View titleGroup = view.findViewById(titleGroupId);
            if (titleGroup != null) {
                titleGroup.setVisibility(View.GONE);
            }
        }

        BaseGridView gridView = findGridView(view);
        if (gridView != null) {
            int padding = Math.round(20 * getResources().getDisplayMetrics().density);
            int top = Math.round(10 * getResources().getDisplayMetrics().density);
            int bottom = Math.round(36 * getResources().getDisplayMetrics().density);
            int spacing = Math.round(4 * getResources().getDisplayMetrics().density);
            gridView.setPadding(padding, top, padding, bottom);
            gridView.setClipToPadding(false);
            gridView.setItemSpacing(spacing);
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setContent(String title, List<VideoItem> videos) {
        items.clear();
        items.addAll(videos);

        adapter.clear();
        if (!videos.isEmpty()) {
            adapter.addAll(0, new ArrayList<>(videos));
        }
        setTitle("");
    }

    /** Updates card state in place, keeping the current scroll position. */
    public void updateItems(List<VideoItem> videos) {
        items.clear();
        items.addAll(videos);
        if (adapter.size() > 0) {
            adapter.notifyItemRangeChanged(0, adapter.size());
        }
    }

    public boolean sameItems(List<VideoItem> other) {
        if (other.size() != items.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).path.equals(other.get(i).path)) {
                return false;
            }
        }
        return true;
    }

    /** Moves remote focus into the grid, used when leaving the sidebar. */
    public void requestGridFocus() {
        View root = getView();
        if (root == null) {
            return;
        }
        BaseGridView gridView = findGridView(root);
        if (gridView == null) {
            return;
        }
        gridView.post(() -> {
            gridView.requestFocus();
            if (!gridView.hasFocus() && gridView.getChildCount() > 0) {
                gridView.getChildAt(0).requestFocus();
            }
        });
    }

    @Nullable
    private static BaseGridView findGridView(View view) {
        if (view instanceof BaseGridView) {
            return (BaseGridView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                BaseGridView result = findGridView(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
