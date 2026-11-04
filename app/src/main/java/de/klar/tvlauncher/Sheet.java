package de.klar.tvlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Menü als schwebende Glastafel (Bedienebene). Schaltflächen darauf sind Füllungen,
 * kein zweites Glas; die fokussierte wird weiß wie auf tvOS.
 */
final class Sheet {
    private final Dialog dialog;
    private final LinearLayout buttons;
    private final FrameLayout extra;
    private final TextView message;
    private final LayoutInflater inflater;

    Sheet(Activity activity, CharSequence title) {
        dialog = new Dialog(activity, R.style.Theme_Klar_Sheet);
        inflater = LayoutInflater.from(activity);
        GlassLayout root = (GlassLayout) inflater.inflate(R.layout.sheet, null);
        // Glas zeigt weichgezeichnet, was dahinter liegt: Bildschirmfoto beim Öffnen
        View content = activity.getWindow().getDecorView();
        if (content.getWidth() > 0) {
            root.setBackdrop(Aurora.snapshot(content), Aurora.BLUR_DOWNSCALE);
        }
        ((TextView) root.findViewById(R.id.sheet_title)).setText(title);
        buttons = root.findViewById(R.id.sheet_buttons);
        extra = root.findViewById(R.id.sheet_extra);
        message = root.findViewById(R.id.sheet_message);
        dialog.setContentView(root);
    }

    Sheet message(CharSequence text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        return this;
    }

    Sheet extra(View view) {
        extra.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        extra.setVisibility(View.VISIBLE);
        return this;
    }

    Sheet button(CharSequence label, Runnable action) {
        return add(label, action, false);
    }

    /** Hauptaktion: höchstens eine pro Menü, farbig hinterlegt. */
    Sheet primary(CharSequence label, Runnable action) {
        return add(label, action, true);
    }

    private Sheet add(CharSequence label, Runnable action, boolean primary) {
        TextView b = (TextView) inflater.inflate(R.layout.sheet_button, buttons, false);
        if (primary) b.setBackgroundResource(R.drawable.sheet_button_primary);
        b.setText(label);
        b.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) action.run();
        });
        buttons.addView(b);
        return this;
    }

    void show() {
        dialog.show();
        if (extra.getChildCount() == 0 && buttons.getChildCount() > 0) buttons.getChildAt(0).requestFocus();
    }
}
