package com.dismal.tvplayer.player;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.dismal.tvplayer.R;
import com.dismal.tvplayer.data.AppConfig;
import com.dismal.tvplayer.data.LibraryStore;
import com.dismal.tvplayer.util.TimeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fullscreen playback with a Google Play Movies style overlay: title at the top,
 * a time bar with elapsed / duration, a centered red play-pause button flanked
 * by skip and 10 second seek buttons, and secondary track options on the right.
 *
 * Remote: OK toggles playback, LEFT/RIGHT seek 10 seconds, UP opens the
 * controls, DOWN/OK inside the controls activates the focused button, BACK
 * closes the controls or the player, MENU/INFO opens the track options.
 */
public class PlayerActivity extends FragmentActivity {

    public static final String EXTRA_PATHS = "extra_paths";
    public static final String EXTRA_TITLES = "extra_titles";
    public static final String EXTRA_URIS = "extra_uris";
    public static final String EXTRA_INDEX = "extra_index";

    private static final String TAG = "PlayerActivity";
    private static final long SEEK_STEP_MS = 10_000L;
    private static final long SEEK_STEP_LONG_MS = 60_000L;
    private static final long HIDE_CONTROLS_MS = 4_000L;
    private static final long INDICATOR_MS = 900L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private AppConfig config;
    private LibraryStore store;

    private PlayerView playerView;
    private ExoPlayer player;

    private String[] paths = new String[0];
    private String[] titles = new String[0];
    private String[] uris = new String[0];
    private int startIndex;
    private String currentPath;

    private ViewGroup controlsLayer;
    private ScrollView panelScroll;
    private LinearLayout panelList;
    private TextView panelTitle;
    private TextView titleView;
    private TextView metaView;
    private TextView positionView;
    private TextView durationView;
    private TextView seekIndicator;
    private TextView errorDetail;
    private SeekBar seekBar;
    private View buffering;
    private View errorPanel;
    private ImageButton playPauseButton;
    private ImageButton favoriteButton;

    private boolean controlsVisible = true;
    private boolean panelOpen;

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying() && !panelOpen) {
                hideControls();
            }
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (controlsVisible && player != null) {
                handler.postDelayed(this, 500L);
            }
        }
    };

    private final Runnable hideIndicatorRunnable = () -> seekIndicator.setVisibility(View.GONE);

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePlayPause();
            if (isPlaying) {
                scheduleHide();
            } else {
                handler.removeCallbacks(hideControlsRunnable);
            }
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            buffering.setVisibility(state == Player.STATE_BUFFERING
                    ? View.VISIBLE : View.GONE);
            if (state == Player.STATE_READY) {
                errorPanel.setVisibility(View.GONE);
                updateMeta();
                updatePlayPause();
            } else if (state == Player.STATE_ENDED) {
                onPlaybackEnded();
            }
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            if (mediaItem != null && mediaItem.mediaId != null) {
                if (panelOpen) {
                    closePanel();
                }
                currentPath = mediaItem.mediaId;
                store.markPlayed(currentPath);
                updateMeta();
                updateFavoriteIcon();
                updatePlayPause();
            }
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            updatePlayPause();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.e(TAG, "Playback failed", error);
            buffering.setVisibility(View.GONE);
            errorDetail.setText(getString(R.string.playback_error_hint)
                    + "\n" + error.getErrorCodeName());
            errorPanel.setVisibility(View.VISIBLE);
            showControls();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = AppConfig.get(this);
        store = LibraryStore.get(this);

        readExtras();
        if (paths.length == 0) {
            finish();
            return;
        }

        setContentView(R.layout.activity_player);
        bindViews();
        hideSystemUi();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setupButtons();
        initPlayer();
    }

    private void readExtras() {
        Intent intent = getIntent();
        String[] extraPaths = intent.getStringArrayExtra(EXTRA_PATHS);
        String[] extraTitles = intent.getStringArrayExtra(EXTRA_TITLES);
        String[] extraUris = intent.getStringArrayExtra(EXTRA_URIS);
        if (extraPaths == null || extraPaths.length == 0) {
            return;
        }
        paths = extraPaths;
        titles = extraTitles != null && extraTitles.length == extraPaths.length
                ? extraTitles : new String[extraPaths.length];
        uris = extraUris != null && extraUris.length == extraPaths.length
                ? extraUris : new String[extraPaths.length];
        for (int i = 0; i < paths.length; i++) {
            if (titles[i] == null || titles[i].isEmpty()) {
                titles[i] = new File(paths[i]).getName();
            }
            if (uris[i] == null || uris[i].isEmpty()) {
                uris[i] = Uri.fromFile(new File(paths[i])).toString();
            }
        }
        startIndex = Math.max(0, Math.min(paths.length - 1,
                intent.getIntExtra(EXTRA_INDEX, 0)));
    }

    private void bindViews() {
        playerView = findViewById(R.id.player_view);
        controlsLayer = findViewById(R.id.controls_layer);
        panelScroll = findViewById(R.id.panel_scroll);
        panelList = findViewById(R.id.panel_list);
        panelTitle = findViewById(R.id.panel_title);
        titleView = findViewById(R.id.title);
        metaView = findViewById(R.id.meta);
        positionView = findViewById(R.id.position);
        durationView = findViewById(R.id.duration);
        seekIndicator = findViewById(R.id.seek_indicator);
        errorDetail = findViewById(R.id.error_detail);
        seekBar = findViewById(R.id.seek_bar);
        buffering = findViewById(R.id.buffering);
        errorPanel = findViewById(R.id.error_panel);
        playPauseButton = findViewById(R.id.btn_play_pause);
        favoriteButton = findViewById(R.id.btn_favorite);
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(playerListener);
        playerView.setPlayer(player);
        playerView.setResizeMode(resizeMode(config.getZoomMode()));

        currentPath = paths[startIndex];
        long resumePosition = store.getPosition(currentPath);
        preparePlaylist(resumePosition);
        player.setPlayWhenReady(true);
        store.markPlayed(currentPath);

        updateMeta();
        updateFavoriteIcon();
        updatePlayPause();

        controlsLayer.post(() -> {
            showControls();
        });
    }

    /** Builds the playlist from the current section and prepares playback. */
    private void preparePlaylist(long startPosition) {
        if (player == null) {
            return;
        }
        List<MediaItem> mediaItems = new ArrayList<>();
        for (int i = 0; i < paths.length; i++) {
            mediaItems.add(buildMediaItem(i));
        }

        int index = indexOfPath(currentPath);
        int start = index;
        if (config.getPlayMode() == AppConfig.PLAY_ONCE) {
            mediaItems = new ArrayList<>(Collections.singletonList(mediaItems.get(index)));
            start = 0;
        }

        player.setMediaItems(mediaItems, start, Math.max(0L, startPosition));
        applyPlayModeFlags();
        player.prepare();
    }

    private MediaItem buildMediaItem(int index) {
        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(Uri.parse(uris[index]))
                .setMediaId(paths[index])
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(titles[index])
                        .build());
        List<MediaItem.SubtitleConfiguration> subtitles = SidecarSubtitles.find(paths[index]);
        if (!subtitles.isEmpty()) {
            builder.setSubtitleConfigurations(subtitles);
        }
        return builder.build();
    }

    private void applyPlayModeFlags() {
        int mode = config.getPlayMode();
        if (mode == AppConfig.PLAY_REPEAT_ONE) {
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else if (mode == AppConfig.PLAY_REPEAT_ALL || mode == AppConfig.PLAY_RANDOM) {
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
        } else {
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
        player.setShuffleModeEnabled(mode == AppConfig.PLAY_RANDOM);
    }

    private void setupButtons() {
        playPauseButton.setOnClickListener(v -> togglePlayback());

        ImageButton rewind = findViewById(R.id.btn_rewind);
        rewind.setOnClickListener(v -> seekBy(-SEEK_STEP_MS));
        rewind.setOnLongClickListener(v -> {
            seekBy(-SEEK_STEP_LONG_MS);
            return true;
        });

        ImageButton forward = findViewById(R.id.btn_forward);
        forward.setOnClickListener(v -> seekBy(SEEK_STEP_MS));
        forward.setOnLongClickListener(v -> {
            seekBy(SEEK_STEP_LONG_MS);
            return true;
        });

        ImageButton previous = findViewById(R.id.btn_previous);
        previous.setOnClickListener(v -> playPrevious());
        ImageButton next = findViewById(R.id.btn_next);
        next.setOnClickListener(v -> playNext());

        ImageButton repeat = findViewById(R.id.btn_repeat);
        repeat.setOnClickListener(v -> showPlayModePanel());
        ImageButton subtitles = findViewById(R.id.btn_subtitles);
        subtitles.setOnClickListener(v -> showSubtitlePanel());
        ImageButton audio = findViewById(R.id.btn_audio);
        audio.setOnClickListener(v -> showAudioPanel());
        ImageButton aspect = findViewById(R.id.btn_aspect);
        aspect.setOnClickListener(v -> showAspectPanel());
        favoriteButton.setOnClickListener(v -> toggleFavorite());
    }

    // ------------------------------------------------------------------ controls

    private void showControls() {
        if (panelOpen) {
            return;
        }
        controlsVisible = true;
        controlsLayer.setVisibility(View.VISIBLE);
        controlsLayer.animate().alpha(1f).setDuration(150L).start();
        playPauseButton.requestFocus();
        updateProgress();
        handler.removeCallbacks(progressRunnable);
        handler.post(progressRunnable);
        scheduleHide();
    }

    private void hideControls() {
        if (panelOpen) {
            return;
        }
        controlsVisible = false;
        View focused = getCurrentFocus();
        if (focused != null) {
            focused.clearFocus();
        }
        controlsLayer.animate().alpha(0f).setDuration(180L).withEndAction(() -> {
            if (!controlsVisible) {
                controlsLayer.setVisibility(View.INVISIBLE);
            }
        }).start();
        handler.removeCallbacks(progressRunnable);
        handler.removeCallbacks(hideControlsRunnable);
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideControlsRunnable);
        if (player != null && player.isPlaying() && !panelOpen) {
            handler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_MS);
        }
    }

    private void updatePlayPause() {
        boolean playing = player != null && player.isPlaying();
        playPauseButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        playPauseButton.setContentDescription(getString(playing ? R.string.pause : R.string.play));
    }

    private void updateProgress() {
        if (player == null) {
            return;
        }
        long position = Math.max(0L, player.getCurrentPosition());
        long duration = player.getDuration();
        positionView.setText(TimeUtils.formatDuration(position));
        durationView.setText(duration > 0
                ? TimeUtils.formatDuration(duration)
                : getString(R.string.duration_unknown));
        if (duration > 0) {
            seekBar.setProgress((int) Math.min(1000L, position * 1000L / duration));
        } else {
            seekBar.setProgress(0);
        }
    }

    private void updateMeta() {
        if (currentPath == null) {
            return;
        }
        titleView.setText(titleFor(currentPath));

        StringBuilder meta = new StringBuilder();
        File file = new File(currentPath);
        File folder = file.getParentFile();
        if (folder != null && folder.getName() != null && !folder.getName().isEmpty()) {
            meta.append(folder.getName());
        }
        Format videoFormat = player != null ? player.getVideoFormat() : null;
        if (videoFormat != null && videoFormat.width > 0) {
            if (meta.length() > 0) {
                meta.append("  •  ");
            }
            meta.append(videoFormat.width).append('×').append(videoFormat.height);
        }
        if (file.length() > 0) {
            if (meta.length() > 0) {
                meta.append("  •  ");
            }
            meta.append(formatSize(file.length()));
        }
        metaView.setText(meta.toString());
    }

    private void updateFavoriteIcon() {
        boolean favorite = currentPath != null && store.isFavorite(currentPath);
        favoriteButton.setImageResource(favorite
                ? R.drawable.ic_star : R.drawable.ic_star_outline);
        favoriteButton.setContentDescription(getString(favorite
                ? R.string.remove_favorite : R.string.add_favorite));
    }

    private void toggleFavorite() {
        if (currentPath == null) {
            return;
        }
        boolean added = store.toggleFavorite(currentPath);
        updateFavoriteIcon();
        Toast.makeText(this, added ? R.string.favorite_added : R.string.favorite_removed,
                Toast.LENGTH_SHORT).show();
    }

    private void showIndicator(String text) {
        seekIndicator.setText(text);
        seekIndicator.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideIndicatorRunnable);
        handler.postDelayed(hideIndicatorRunnable, INDICATOR_MS);
    }

    private void togglePlayback() {
        if (player == null) {
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekTo(0);
            player.play();
        } else if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        showIndicator(getString(player.isPlaying() ? R.string.play : R.string.pause));
        updatePlayPause();
    }

    private void seekBy(long deltaMs) {
        if (player == null) {
            return;
        }
        long duration = player.getDuration();
        long target = Math.max(0L, player.getCurrentPosition() + deltaMs);
        if (duration > 0) {
            target = Math.min(target, duration);
        }
        player.seekTo(target);
        updateProgress();

        String sign = deltaMs >= 0 ? "+" : "−";
        String text = sign + TimeUtils.formatDuration(Math.abs(deltaMs));
        if (duration > 0) {
            text += "\n" + TimeUtils.formatDuration(target) + " / "
                    + TimeUtils.formatDuration(duration);
        }
        showIndicator(text);
        scheduleHide();
    }

    private void playNext() {
        moveTo(1);
    }

    private void playPrevious() {
        if (player.getCurrentPosition() > 5_000L) {
            player.seekTo(0);
            showIndicator(TimeUtils.formatDuration(0));
            return;
        }
        moveTo(-1);
    }

    private void moveTo(int direction) {
        int count = player.getMediaItemCount();
        if (count <= 1) {
            return;
        }
        int target = player.getCurrentMediaItemIndex() + direction;
        if (target >= count) {
            target = 0;
        } else if (target < 0) {
            target = count - 1;
        }
        store.setPosition(currentPath, normalizedPosition());
        player.seekToDefaultPosition(target);
        player.play();
        showControls();
    }

    private long normalizedPosition() {
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration > 0 && position >= duration - 5_000L) {
            return 0L;
        }
        return position;
    }

    private void onPlaybackEnded() {
        if (currentPath != null) {
            store.setPosition(currentPath, 0L);
            store.markPlayed(currentPath);
        }
        if (config.getPlayMode() == AppConfig.PLAY_ONCE
                || config.getPlayMode() == AppConfig.PLAY_SEQUENCE) {
            showControls();
            updatePlayPause();
        }
    }

    // ------------------------------------------------------------------ option panels

    private static final class MenuOption {
        final String label;
        final boolean selected;
        final Runnable action;

        MenuOption(String label, boolean selected, Runnable action) {
            this.label = label;
            this.selected = selected;
            this.action = action;
        }
    }

    private void openPanel(String title, List<MenuOption> options) {
        panelOpen = true;
        handler.removeCallbacks(hideControlsRunnable);
        panelTitle.setText(title);
        panelList.removeAllViews();

        LayoutInflater inflater = getLayoutInflater();
        View first = null;
        View checked = null;
        for (MenuOption option : options) {
            View row = inflater.inflate(R.layout.item_option, panelList, false);
            TextView label = row.findViewById(R.id.option_label);
            ImageView check = row.findViewById(R.id.option_check);
            label.setText(option.label);
            check.setVisibility(option.selected ? View.VISIBLE : View.INVISIBLE);
            row.setOnClickListener(v -> {
                closePanel();
                option.action.run();
            });
            panelList.addView(row);
            if (first == null) {
                first = row;
            }
            if (checked == null && option.selected) {
                checked = row;
            }
        }

        panelScroll.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams params = panelScroll.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        panelScroll.setLayoutParams(params);

        controlsLayer.setAlpha(0.25f);
        controlsLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        View focusTarget = checked != null ? checked : first;
        if (focusTarget != null) {
            View target = focusTarget;
            panelScroll.post(() -> {
                int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.74f);
                int contentHeight = panelScroll.getChildAt(0).getHeight();
                if (contentHeight > 0) {
                    ViewGroup.LayoutParams layoutParams = panelScroll.getLayoutParams();
                    layoutParams.height = Math.min(contentHeight, maxHeight);
                    panelScroll.setLayoutParams(layoutParams);
                }
                target.requestFocus();
            });
        }
    }

    private void closePanel() {
        if (!panelOpen) {
            return;
        }
        panelOpen = false;
        panelScroll.setVisibility(View.GONE);
        panelList.removeAllViews();
        controlsLayer.setAlpha(1f);
        controlsLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        showControls();
    }

    private void showSettingsPanel() {
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption(getString(R.string.subtitles), false, this::showSubtitlePanel));
        options.add(new MenuOption(getString(R.string.audio_track), false, this::showAudioPanel));
        options.add(new MenuOption(getString(R.string.aspect_ratio), false, this::showAspectPanel));
        options.add(new MenuOption(getString(R.string.play_mode), false, this::showPlayModePanel));
        options.add(new MenuOption(getString(R.string.video_info), false, this::showInfoDialog));
        openPanel(getString(R.string.player_options), options);
    }

    private void showSubtitlePanel() {
        List<MenuOption> options = new ArrayList<>();
        TrackSelectionParameters parameters = player.getTrackSelectionParameters();
        boolean subtitlesOff = parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        options.add(new MenuOption(getString(R.string.menu_option_off), subtitlesOff,
                this::disableSubtitles));

        int number = 0;
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) {
                    continue;
                }
                number++;
                Format format = group.getTrackFormat(i);
                TrackGroup trackGroup = group.getMediaTrackGroup();
                final int trackIndex = i;
                options.add(new MenuOption(trackLabel(format, number),
                        group.isTrackSelected(i),
                        () -> overrideTrack(C.TRACK_TYPE_TEXT, trackGroup, trackIndex)));
            }
        }
        if (number == 0) {
            options.add(new MenuOption(getString(R.string.info_none), false, () -> {
            }));
        }
        openPanel(getString(R.string.subtitles), options);
    }

    private void showAudioPanel() {
        List<MenuOption> options = new ArrayList<>();
        boolean hasOverride = !player.getTrackSelectionParameters().overrides.isEmpty();
        options.add(new MenuOption(getString(R.string.menu_option_auto), !hasOverride,
                () -> clearTrackOverride(C.TRACK_TYPE_AUDIO)));

        int number = 0;
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) {
                    continue;
                }
                number++;
                Format format = group.getTrackFormat(i);
                TrackGroup trackGroup = group.getMediaTrackGroup();
                final int trackIndex = i;
                options.add(new MenuOption(trackLabel(format, number),
                        group.isTrackSelected(i),
                        () -> overrideTrack(C.TRACK_TYPE_AUDIO, trackGroup, trackIndex)));
            }
        }
        if (number == 0) {
            options.add(new MenuOption(getString(R.string.info_none), false, () -> {
            }));
        }
        openPanel(getString(R.string.audio_track), options);
    }

    private void showAspectPanel() {
        int current = config.getZoomMode();
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption(getString(R.string.aspect_fit),
                current == AppConfig.ZOOM_FIT, () -> setZoomMode(AppConfig.ZOOM_FIT)));
        options.add(new MenuOption(getString(R.string.aspect_stretch),
                current == AppConfig.ZOOM_STRETCH, () -> setZoomMode(AppConfig.ZOOM_STRETCH)));
        options.add(new MenuOption(getString(R.string.aspect_crop),
                current == AppConfig.ZOOM_CROP, () -> setZoomMode(AppConfig.ZOOM_CROP)));
        openPanel(getString(R.string.aspect_ratio), options);
    }

    private void showPlayModePanel() {
        int current = config.getPlayMode();
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption(getString(R.string.mode_once),
                current == AppConfig.PLAY_ONCE, () -> setPlayMode(AppConfig.PLAY_ONCE)));
        options.add(new MenuOption(getString(R.string.mode_sequence),
                current == AppConfig.PLAY_SEQUENCE, () -> setPlayMode(AppConfig.PLAY_SEQUENCE)));
        options.add(new MenuOption(getString(R.string.mode_repeat_all),
                current == AppConfig.PLAY_REPEAT_ALL, () -> setPlayMode(AppConfig.PLAY_REPEAT_ALL)));
        options.add(new MenuOption(getString(R.string.mode_repeat_one),
                current == AppConfig.PLAY_REPEAT_ONE, () -> setPlayMode(AppConfig.PLAY_REPEAT_ONE)));
        options.add(new MenuOption(getString(R.string.mode_random),
                current == AppConfig.PLAY_RANDOM, () -> setPlayMode(AppConfig.PLAY_RANDOM)));
        openPanel(getString(R.string.play_mode), options);
    }

    private void setZoomMode(int mode) {
        config.setZoomMode(mode);
        playerView.setResizeMode(resizeMode(mode));
    }

    private void setPlayMode(int mode) {
        config.setPlayMode(mode);
        boolean ended = player.getPlaybackState() == Player.STATE_ENDED;
        long position = ended ? 0L : player.getCurrentPosition();
        preparePlaylist(position);
        player.setPlayWhenReady(true);
        updatePlayPause();

        int label;
        switch (mode) {
            case AppConfig.PLAY_ONCE:
                label = R.string.mode_once;
                break;
            case AppConfig.PLAY_REPEAT_ALL:
                label = R.string.mode_repeat_all;
                break;
            case AppConfig.PLAY_REPEAT_ONE:
                label = R.string.mode_repeat_one;
                break;
            case AppConfig.PLAY_RANDOM:
                label = R.string.mode_random;
                break;
            case AppConfig.PLAY_SEQUENCE:
            default:
                label = R.string.mode_sequence;
                break;
        }
        showIndicator(getString(label));
    }

    private void overrideTrack(int trackType, TrackGroup group, int trackIndex) {
        TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(trackType, false)
                .setOverrideForType(new TrackSelectionOverride(group, trackIndex))
                .build();
        player.setTrackSelectionParameters(parameters);
    }

    private void clearTrackOverride(int trackType) {
        TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                .buildUpon()
                .clearOverridesOfType(trackType)
                .build();
        player.setTrackSelectionParameters(parameters);
    }

    private void disableSubtitles() {
        TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build();
        player.setTrackSelectionParameters(parameters);
    }

    private void showInfoDialog() {
        StringBuilder info = new StringBuilder();
        info.append(getString(R.string.info_title)).append(": ")
                .append(titleFor(currentPath)).append('\n');
        info.append(getString(R.string.info_path)).append(": ")
                .append(currentPath).append('\n');

        Format video = player.getVideoFormat();
        if (video != null) {
            if (video.width > 0) {
                info.append(getString(R.string.info_resolution)).append(": ")
                        .append(video.width).append('×').append(video.height).append('\n');
            }
            if (video.frameRate > 0) {
                info.append(String.format(Locale.US, "%.2f fps", video.frameRate))
                        .append('\n');
            }
        }
        long duration = player.getDuration();
        if (duration > 0) {
            info.append(getString(R.string.info_duration)).append(": ")
                    .append(TimeUtils.formatDuration(duration)).append('\n');
        }
        File file = new File(currentPath);
        if (file.length() > 0) {
            info.append(getString(R.string.info_size)).append(": ")
                    .append(formatSize(file.length())).append('\n');
        }
        Format audio = player.getAudioFormat();
        if (audio != null) {
            info.append(getString(R.string.info_audio)).append(": ")
                    .append(trackLabel(audio, 1)).append('\n');
        }
        info.append(getString(R.string.info_subtitles)).append(": ")
                .append(subtitleSummary());

        new AlertDialog.Builder(this)
                .setTitle(R.string.video_info)
                .setMessage(info.toString())
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    private String subtitleSummary() {
        int count = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSupported(i)) {
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            return getString(R.string.info_none);
        }
        return count + " " + getString(R.string.subtitles).toLowerCase(Locale.US);
    }

    // ------------------------------------------------------------------ helpers

    private int indexOfPath(String path) {
        if (path != null) {
            for (int i = 0; i < paths.length; i++) {
                if (path.equals(paths[i])) {
                    return i;
                }
            }
        }
        return Math.max(0, Math.min(paths.length - 1, startIndex));
    }

    private String titleFor(String path) {
        if (path != null) {
            for (int i = 0; i < paths.length; i++) {
                if (path.equals(paths[i])) {
                    return titles[i];
                }
            }
            return new File(path).getName();
        }
        return getString(R.string.player_title_unknown);
    }

    private static int resizeMode(int zoomMode) {
        switch (zoomMode) {
            case AppConfig.ZOOM_STRETCH:
                return AspectRatioFrameLayout.RESIZE_MODE_FILL;
            case AppConfig.ZOOM_CROP:
                return AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            case AppConfig.ZOOM_FIT:
            default:
                return AspectRatioFrameLayout.RESIZE_MODE_FIT;
        }
    }

    private static String trackLabel(Format format, int number) {
        String label = format.label;
        if (label == null || label.isEmpty()) {
            label = format.language;
        }
        if (label == null || label.isEmpty()) {
            label = format.sampleMimeType;
        }
        if (label == null || label.isEmpty()) {
            label = "Track " + number;
        }
        if (format.channelCount > 2) {
            label += " (" + format.channelCount + " ch)";
        }
        return label;
    }

    private static String formatSize(long bytes) {
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.0f MB", mb);
        }
        return String.format(Locale.US, "%.1f GB", mb / 1024.0);
    }

    private void savePosition() {
        if (player == null || currentPath == null) {
            return;
        }
        store.setPosition(currentPath, normalizedPosition());
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (code) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    togglePlayback();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    player.play();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    player.pause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekBy(SEEK_STEP_MS);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekBy(-SEEK_STEP_MS);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    playNext();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    playPrevious();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    finish();
                    return true;
                default:
                    break;
            }

            if (panelOpen) {
                if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
                    closePanel();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO) {
                showSettingsPanel();
                return true;
            }

            if (controlsVisible) {
                if (code == KeyEvent.KEYCODE_BACK) {
                    hideControls();
                    return true;
                }
                scheduleHide();
                return super.dispatchKeyEvent(event);
            }

            switch (code) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    togglePlayback();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    seekBy(-SEEK_STEP_MS);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    seekBy(SEEK_STEP_MS);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    showControls();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    finish();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
            savePosition();
        }
        handler.removeCallbacks(progressRunnable);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null && controlsVisible) {
            handler.post(progressRunnable);
        }
    }

    @Override
    public void finish() {
        savePosition();
        super.finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            playerView.setPlayer(null);
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
