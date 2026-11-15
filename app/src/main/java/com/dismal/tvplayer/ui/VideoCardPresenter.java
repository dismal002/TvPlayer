package com.dismal.tvplayer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.leanback.widget.Presenter;

import com.dismal.tvplayer.R;
import com.dismal.tvplayer.data.VideoItem;
import com.dismal.tvplayer.util.ThumbnailLoader;

/** Leanback card presenter: thumbnail with a colored title strip below it. */
public class VideoCardPresenter extends Presenter {

    public interface Listener {
        void onVideoClicked(VideoItem item);

        void onFavoriteToggled(VideoItem item);
    }

    private final ThumbnailLoader thumbnails;
    private final Listener listener;

    public VideoCardPresenter(ThumbnailLoader thumbnails, Listener listener) {
        this.thumbnails = thumbnails;
        this.listener = listener;
    }

    static class CardHolder extends ViewHolder {
        final ImageView thumb;
        final TextView title;
        final TextView meta;
        VideoItem item;

        CardHolder(View view) {
            super(view);
            thumb = view.findViewById(R.id.thumb);
            title = view.findViewById(R.id.title);
            meta = view.findViewById(R.id.meta);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_video, parent, false);
        CardHolder holder = new CardHolder(view);

        view.setOnClickListener(v -> {
            if (holder.item != null && listener != null) {
                listener.onVideoClicked(holder.item);
            }
        });

        view.setOnLongClickListener(v -> {
            if (holder.item != null && listener != null) {
                listener.onFavoriteToggled(holder.item);
                return true;
            }
            return false;
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        VideoItem video = (VideoItem) item;
        CardHolder holder = (CardHolder) viewHolder;

        holder.item = video;
        holder.title.setText(video.title);
        holder.meta.setText(video.folderName());
        holder.view.setContentDescription(video.title);

        thumbnails.load(video, holder.thumb);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        CardHolder holder = (CardHolder) viewHolder;
        holder.item = null;
        holder.thumb.setImageDrawable(null);
        thumbnails.release(holder.thumb);
    }
}
