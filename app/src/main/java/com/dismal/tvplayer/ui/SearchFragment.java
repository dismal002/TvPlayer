package com.dismal.tvplayer.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.leanback.app.SearchSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.SearchOrbView;

import com.dismal.tvplayer.R;
import com.dismal.tvplayer.data.MediaLibrary;
import com.dismal.tvplayer.data.VideoItem;
import com.dismal.tvplayer.player.PlayerActivity;
import com.dismal.tvplayer.util.ThumbnailLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app search following the Android TV Leanback search sample: a
 * SearchSupportFragment with the standard search bar, speech orb and a
 * results row backed by the local video library.
 */
public class SearchFragment extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider {

    private static final int REQUEST_SPEECH = 0x10;

    private final List<VideoItem> library = new ArrayList<>();
    private final List<VideoItem> lastResults = new ArrayList<>();

    private ArrayObjectAdapter rowsAdapter;
    private ArrayObjectAdapter resultsAdapter;
    private ThumbnailLoader thumbnails;
    private ExecutorService executor;
    private String query = "";
    private boolean opening;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        thumbnails = new ThumbnailLoader(requireContext());
        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        resultsAdapter = new ArrayObjectAdapter(new VideoCardPresenter(thumbnails,
                new VideoCardPresenter.Listener() {
                    @Override
                    public void onVideoClicked(VideoItem item) {
                        openPlayer(item);
                    }

                    @Override
                    public void onFavoriteToggled(VideoItem item) {
                        // Long-press has no meaning in search results.
                    }
                }));

        setSearchResultProvider(this);
        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (item instanceof VideoItem) {
                openPlayer((VideoItem) item);
            }
        });
        setSpeechRecognitionCallback(() -> {
            try {
                startActivityForResult(getRecognizerIntent(), REQUEST_SPEECH);
            } catch (ActivityNotFoundException ignored) {
                // No recognizer installed; the on-screen keyboard still works.
            }
        });

        final Context context = requireContext().getApplicationContext();
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<VideoItem> loaded = MediaLibrary.scan(context);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                library.clear();
                library.addAll(loaded);
                publishResults(query);
            });
        });
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setSearchAffordanceColors(new SearchOrbView.Colors(
                getResources().getColor(R.color.vp_blue, null),
                getResources().getColor(R.color.vp_teal, null),
                getResources().getColor(R.color.text_primary, null)));
    }

    @Override
    public void onResume() {
        super.onResume();
        opening = false;
    }

    @Override
    public void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_SPEECH && resultCode == Activity.RESULT_OK && data != null) {
            setSearchQuery(data, true);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return rowsAdapter;
    }

    @Override
    public boolean onQueryTextChange(String newQuery) {
        query = newQuery;
        publishResults(query);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String newQuery) {
        query = newQuery;
        publishResults(query);
        return true;
    }

    private void publishResults(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        lastResults.clear();
        if (!q.isEmpty()) {
            String needle = q.toLowerCase(Locale.US);
            for (VideoItem item : library) {
                if (item.title.toLowerCase(Locale.US).contains(needle)
                        || item.path.toLowerCase(Locale.US).contains(needle)) {
                    lastResults.add(item);
                }
            }
            Collections.sort(lastResults, (a, b) -> a.title.compareToIgnoreCase(b.title));
        }

        resultsAdapter.clear();
        if (!lastResults.isEmpty()) {
            resultsAdapter.addAll(0, new ArrayList<>(lastResults));
        }

        rowsAdapter.clear();
        if (!q.isEmpty()) {
            HeaderItem header = new HeaderItem(lastResults.isEmpty()
                    ? getString(R.string.no_search_results, q)
                    : getString(R.string.search_results, q));
            rowsAdapter.add(new ListRow(header, resultsAdapter));
        }
    }

    private void openPlayer(VideoItem video) {
        int index = lastResults.indexOf(video);
        if (opening || index < 0) {
            return;
        }
        opening = true;

        String[] paths = new String[lastResults.size()];
        String[] titles = new String[lastResults.size()];
        String[] uris = new String[lastResults.size()];
        for (int i = 0; i < lastResults.size(); i++) {
            VideoItem item = lastResults.get(i);
            paths[i] = item.path;
            titles[i] = item.title;
            uris[i] = item.uri;
        }

        Intent intent = new Intent(requireContext(), PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_PATHS, paths);
        intent.putExtra(PlayerActivity.EXTRA_TITLES, titles);
        intent.putExtra(PlayerActivity.EXTRA_URIS, uris);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index);
        startActivity(intent);
    }
}
