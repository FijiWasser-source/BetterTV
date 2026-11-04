package de.klar.tvlauncher;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class TileAdapter extends RecyclerView.Adapter<TileAdapter.Holder> {

    interface Listener {
        void onOpen(AppEntry entry);
        void onMenu(AppEntry entry);
    }

    private final Listener listener;
    private List<AppEntry> items = new ArrayList<>();

    TileAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<AppEntry> entries) {
        items = entries;
        notifyDataSetChanged();
    }

    int indexOf(String key) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).key.equals(key)) return i;
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tile, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppEntry e = items.get(position);
        h.card.setTag(e.key);
        h.card.setContentDescription(e.label);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.iconGroup.setVisibility(View.GONE);
        } else {
            h.banner.setVisibility(View.GONE);
            h.iconGroup.setVisibility(View.VISIBLE);
            h.icon.setImageDrawable(e.icon);
            h.label.setText(e.label);
        }
        h.pin.setVisibility(e.pinned ? View.VISIBLE : View.GONE);

        h.card.setOnClickListener(v -> listener.onOpen(e));
        h.card.setOnLongClickListener(v -> { listener.onMenu(e); return true; });
        h.card.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                listener.onMenu(e);
                return true;
            }
            return false;
        });
        h.card.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.06f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(120).start();
            v.setElevation(focused ? 12f : 0f);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final View iconGroup;
        final ImageView icon;
        final TextView label;
        final View pin;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            iconGroup = v.findViewById(R.id.icon_group);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            pin = v.findViewById(R.id.pin);
        }
    }
}
