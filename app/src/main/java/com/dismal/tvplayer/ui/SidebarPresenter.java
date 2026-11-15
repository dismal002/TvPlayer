package com.dismal.tvplayer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.leanback.widget.Presenter;

import com.dismal.tvplayer.R;

/**
 * Renders the sidebar like Leanback's header list: a 32dp icon and a label,
 * with unselected rows dimmed to half opacity and the current one at full
 * brightness. There is no row background, matching RowHeaderPresenter.
 */
public class SidebarPresenter extends Presenter {

    public interface Listener {
        void onItemClicked(SidebarItem item);
    }

    private static final float UNSELECTED_ALPHA = 0.5f;

    private final Listener listener;

    public SidebarPresenter(Listener listener) {
        this.listener = listener;
    }

    static class Holder extends ViewHolder {
        final ImageView icon;
        final TextView label;

        Holder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            label = view.findViewById(R.id.label);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sidebar, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SidebarItem sidebarItem = (SidebarItem) item;
        Holder holder = (Holder) viewHolder;
        View row = holder.view;

        holder.label.setText(sidebarItem.label);
        holder.icon.setImageResource(sidebarItem.iconRes);
        holder.icon.setVisibility(sidebarItem.iconRes == 0 ? View.GONE : View.VISIBLE);

        row.setFocusable(true);
        row.setClickable(true);
        row.setAlpha(sidebarItem.selected || row.hasFocus() ? 1f : UNSELECTED_ALPHA);
        row.setOnFocusChangeListener((v, hasFocus) ->
                v.setAlpha(hasFocus || sidebarItem.selected ? 1f : UNSELECTED_ALPHA));

        final SidebarItem clicked = sidebarItem;
        row.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClicked(clicked);
            }
        });
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        Holder holder = (Holder) viewHolder;
        holder.view.setOnClickListener(null);
        holder.view.setOnFocusChangeListener(null);
        holder.icon.setImageDrawable(null);
        holder.view.setSelected(false);
    }
}
