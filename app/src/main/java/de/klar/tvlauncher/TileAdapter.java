package de.klar.tvlauncher;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TileAdapter extends RecyclerView.Adapter<TileAdapter.Holder> {

    interface Listener {
        void onOpen(AppEntry entry);
        void onMenu(AppEntry entry);
    }

    private static final int SURFACE = Color.rgb(0x16, 0x1B, 0x30);
    private static final float FOCUS_SCALE = 1.12f;

    private final Listener listener;
    private final Map<String, Integer> colors = new HashMap<>();
    private final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
    private List<AppEntry> items = new ArrayList<>();
    private float density = 1f;

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

    /** Grundfarbe der App (zwischengespeichert). */
    private int colorOf(AppEntry e) {
        Integer c = colors.get(e.key);
        if (c == null) {
            c = Tint.average(e.banner != null ? e.banner : e.icon, SURFACE);
            colors.put(e.key, c);
        }
        return c;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        density = parent.getResources().getDisplayMetrics().density;
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tile, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppEntry e = items.get(position);
        int color = colorOf(e);
        h.card.setTag(e.key);
        h.card.setContentDescription(e.label);
        h.label.setText(e.label);
        h.card.setClipToOutline(true);

        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.card.setBackgroundTintList(null);
        } else {
            h.banner.setVisibility(View.GONE);
            h.icon.setVisibility(View.VISIBLE);
            h.icon.setImageDrawable(e.icon);
            // Inhaltsebene: ruhige Fläche im Farbschema, nur leicht in Richtung App-Farbe
            h.card.setBackgroundTintList(ColorStateList.valueOf(Tint.mix(SURFACE, color, 0.3f)));
        }
        h.pin.setVisibility(e.pinned ? View.VISIBLE : View.GONE);
        applyFocus(h, h.card.isFocused(), false);

        h.card.setOnClickListener(v -> {
            // kurzes "Eindrücken" wie bei der Siri Remote
            v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80).withEndAction(() -> {
                applyFocus(h, v.isFocused(), true);
                listener.onOpen(e);
            }).start();
        });
        h.card.setOnLongClickListener(v -> { listener.onMenu(e); return true; });
        h.card.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                listener.onMenu(e);
                return true;
            }
            return false;
        });
        h.card.setOnFocusChangeListener((v, focused) -> {
            applyFocus(h, focused, true);
        });
    }

    private void applyFocus(Holder h, boolean focused, boolean animate) {
        float scale = focused ? FOCUS_SCALE : 1f;
        float lift = focused ? 22f * density : 0f;
        float alpha = focused ? 1f : 0f;
        if (animate) {
            h.card.animate().scaleX(scale).scaleY(scale).translationZ(lift)
                    .setInterpolator(ease).setDuration(180).start();
            h.label.animate().alpha(alpha).translationY(focused ? 4f * density : 0f).setDuration(180).start();
            h.sheen.animate().alpha(alpha).setDuration(180).start();
        } else {
            h.card.setScaleX(scale); h.card.setScaleY(scale); h.card.setTranslationZ(lift);
            h.label.setAlpha(alpha); h.sheen.setAlpha(alpha);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final View sheen;
        final View pin;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            sheen = v.findViewById(R.id.sheen);
            pin = v.findViewById(R.id.pin);
        }
    }
}
