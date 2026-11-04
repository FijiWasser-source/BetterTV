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
        default void onFocus(AppEntry entry) {}
    }

    private static final int SURFACE = Color.rgb(0x1C, 0x24, 0x30);
    private final Listener listener;
    private final int layoutRes;
    private final float focusScale;
    private final boolean tintIconTiles;
    private final boolean abend;
    private final Map<String, Integer> colors = new HashMap<>();
    private final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
    private List<AppEntry> items = new ArrayList<>();
    private String iconStyle = IconStyle.ORIGINAL;

    void setIconStyle(String style) {
        if (style.equals(iconStyle)) return;
        iconStyle = style;
        notifyDataSetChanged();
    }
    private float density = 1f;

    /** Raster ("Alle Apps"): feste Kacheln der Inhaltsebene. */
    TileAdapter(Listener listener) {
        this(listener, R.layout.item_tile, 1.12f, true);
    }

    /**
     * @param tintIconTiles Kacheln ohne Banner in App-Farbe einfärben (nur im Raster;
     *                      im Glas-Dock bleiben sie neutrale Füllungen)
     */
    TileAdapter(Listener listener, int layoutRes, float focusScale, boolean tintIconTiles) {
        this(listener, layoutRes, focusScale, tintIconTiles, false);
    }

    /** @param abend Design "Abend": Nummern, Name immer sichtbar, oranges Leuchten bei Fokus */
    TileAdapter(Listener listener, int layoutRes, float focusScale, boolean tintIconTiles, boolean abend) {
        this.abend = abend;
        this.listener = listener;
        this.layoutRes = layoutRes;
        this.focusScale = focusScale;
        this.tintIconTiles = tintIconTiles;
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
        View v = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppEntry e = items.get(position);
        int color = colorOf(e);
        h.card.setTag(e.key);
        boolean outline = IconStyle.isGlass(iconStyle);
        if (abend && !(h.card.getBackground() instanceof AbendTile
                && ((AbendTile) h.card.getBackground()).outline == outline)) {
            h.card.setBackground(new AbendTile(density, 16, outline));
            if (h.label != null) h.label.setTextColor(outline ? 0xFFFFFFFF : AbendTile.INK);
        }
        if (h.number != null) {
            // Nummer = Taste auf der Fernbedienung (nur echte Apps, 1–9)
            boolean app = e.launchIntent != null;
            h.number.setText(app && position < 9 ? String.valueOf(position + 1) : "");
        }
        h.card.setContentDescription(e.label);
        if (h.label != null) h.label.setText(e.label);
        h.card.setClipToOutline(true);

        if (e.banner != null) {
            // Im Design "Abend" runde Ecken direkt im Bild – bleibt auch bei Fokus (Software-Zeichnung) rund
            // Runde Ecken im Design "Abend"; Symbol-Stil gilt überall in Klar
            boolean styleBanner = abend || !IconStyle.ORIGINAL.equals(iconStyle);
            h.banner.setImageDrawable(styleBanner ? new RoundedDrawable(e.banner, abend ? 10 * density : 0, iconStyle) : e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.card.setBackgroundTintList(null);
        } else {
            h.banner.setVisibility(View.GONE);
            h.icon.setVisibility(View.VISIBLE);
            // App-Symbole (nicht "Alle Apps"/"Einstellungen") im gewählten Stil
            boolean styled = e.launchIntent != null && !IconStyle.ORIGINAL.equals(iconStyle);
            h.icon.setImageDrawable(styled ? new RoundedDrawable(e.icon, abend ? 10 * density : 0, iconStyle) : e.icon);
            // Inhaltsebene: ruhige Fläche im Farbschema, nur leicht in Richtung App-Farbe
            if (tintIconTiles) {
                h.card.setBackgroundTintList(ColorStateList.valueOf(Tint.mix(SURFACE, color, 0.3f)));
            }
        }
        if (h.pin != null) h.pin.setVisibility(e.pinned ? View.VISIBLE : View.GONE);
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
            if (focused) listener.onFocus(e);
        });
    }

    private void applyFocus(Holder h, boolean focused, boolean animate) {
        if (abend) {
            AbendTile.focus(h.card, focused);
            boolean ol = h.card.getBackground() instanceof AbendTile && ((AbendTile) h.card.getBackground()).outline;
            if (h.number != null) h.number.setTextColor(focused ? AbendTile.ORANGE : ol ? 0xFFA9ADB5 : AbendTile.INK_SOFT);
            float s = focused ? focusScale : 1f;
            if (animate) h.card.animate().scaleX(s).scaleY(s).setInterpolator(ease).setDuration(160).start();
            else { h.card.setScaleX(s); h.card.setScaleY(s); }
            return;
        }
        float scale = focused ? focusScale : 1f;
        float lift = focused ? 18f * density : 0f;
        float alpha = focused ? 1f : 0f;
        if (animate) {
            h.card.animate().scaleX(scale).scaleY(scale).translationZ(lift)
                    .setInterpolator(ease).setDuration(180).start();
            if (h.label != null) {
                h.label.animate().alpha(alpha).translationY(focused ? 4f * density : 0f).setDuration(180).start();
            }
            if (h.sheen != null) h.sheen.animate().alpha(alpha).setDuration(180).start();
        } else {
            h.card.setScaleX(scale); h.card.setScaleY(scale); h.card.setTranslationZ(lift);
            if (h.label != null) h.label.setAlpha(alpha);
            if (h.sheen != null) h.sheen.setAlpha(alpha);
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
        final TextView number;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            sheen = v.findViewById(R.id.sheen);
            pin = v.findViewById(R.id.pin);
            number = v.findViewById(R.id.number);
        }
    }
}
