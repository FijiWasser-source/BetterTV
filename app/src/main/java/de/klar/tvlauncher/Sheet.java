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
 * Menü im Stil der tvOS-Kontextmenüs: mittige, abgerundete Tafel, Schaltflächen
 * untereinander, die fokussierte wird weiß.
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
        View root = inflater.inflate(R.layout.sheet, null);
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
        TextView b = (TextView) inflater.inflate(R.layout.sheet_button, buttons, false);
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
