package de.klar.tvlauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** "Alle Apps": das vollständige Raster, damit die Startansicht leer und ruhig bleiben kann. */
public class AppsActivity extends Activity implements TileAdapter.Listener {

    private Prefs prefs;
    private TileAdapter adapter;
    private RecyclerView grid;
    private String focusKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        prefs = new Prefs(this);
        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 5));
        grid.setItemAnimator(null);
        adapter = new TileAdapter(this);
        adapter.setIconStyle(prefs.iconStyle());
        grid.setAdapter(adapter);

        View root = findViewById(R.id.root);
        ImageView wallpaper = findViewById(R.id.wallpaper);
        root.post(() -> {
            if (root.getWidth() == 0) return;
            wallpaper.setImageBitmap(Wallpaper.load(this, root.getWidth(), root.getHeight()).blurred);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) focusKey = (String) focused.getTag();
        adapter.submit(AppLoader.load(this, prefs));
        int pos = focusKey == null ? 0 : Math.max(0, adapter.indexOf(focusKey));
        grid.scrollToPosition(pos);
        grid.post(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
            if (h != null) h.itemView.findViewById(R.id.card).requestFocus();
        });
    }

    @Override
    public void onOpen(AppEntry entry) {
        focusKey = entry.key;
        AppMenu.open(this, entry);
    }

    @Override
    public void onMenu(AppEntry entry) {
        focusKey = entry.key;
        AppMenu.show(this, prefs, entry, this::reload);
    }
}
