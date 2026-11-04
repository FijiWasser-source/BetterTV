package de.bettertv.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Füllt das "HEUTE"-Feld: Tageswetter und die nächsten Termine. */
final class DayPanel {
    private DayPanel() {}

    static final int MAX_ROWS = 4;

    /** Termine, die noch nicht vorbei sind (ganztägige immer). */
    static List<CalendarIcs.Event> upcoming(List<CalendarIcs.Event> all, long now) {
        List<CalendarIcs.Event> out = new ArrayList<>();
        if (all == null) return out;
        for (CalendarIcs.Event e : all) if (e.allDay || Math.max(e.end, e.start + 1) > now) out.add(e);
        return out;
    }

    static String weatherLine(Weather.Daily d) {
        String desc = Weather.describe(d.code);
        String range = Math.round(d.min) + "° bis " + Math.round(d.max) + "°";
        return desc.isEmpty() ? range : desc + ", " + range;
    }

    static void style(View panel) {
        float d = panel.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setColor(0xF2212328);            // dunkles Glas, deckend genug für Himmel & Wolken
        fill.setCornerRadius(18 * d);
        android.graphics.drawable.LayerDrawable bg = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{fill, new AbendTile(d, 18, true)});
        int m = Math.round(AbendTile.GLOW_DP * d);   // Füllung genau innerhalb der orangen Linie
        bg.setLayerInset(0, m, m, m, m);
        panel.setBackground(bg);
    }

    /**
     * @param daily    null = (noch) kein Wetter
     * @param events   null = kein Kalender verbunden
     */
    static void bind(View panel, Weather.Daily daily, List<CalendarIcs.Event> events, boolean calendarError, long now) {
        Context c = panel.getContext();
        Typeface rounded = Fonts.rounded(c);
        TextView weather = panel.findViewById(R.id.day_weather);
        TextView rain = panel.findViewById(R.id.day_rain);
        weather.setTypeface(rounded);
        if (daily != null) {
            weather.setVisibility(View.VISIBLE);
            weather.setText(weatherLine(daily));
            boolean showRain = daily.rain >= 10;
            rain.setVisibility(showRain ? View.VISIBLE : View.GONE);
            if (showRain) rain.setText((daily.rain >= 50 ? "Schirm mitnehmen · Regen " : "Regenrisiko ") + daily.rain + " %");
        } else {
            weather.setVisibility(View.GONE);
            rain.setVisibility(View.GONE);
        }
        panel.findViewById(R.id.day_rule).setVisibility(daily != null ? View.VISIBLE : View.GONE);

        LinearLayout list = panel.findViewById(R.id.day_events);
        list.removeAllViews();
        float dens = c.getResources().getDisplayMetrics().density;
        if (events == null) {
            list.addView(note(c, calendarError ? "Kalender gerade nicht erreichbar"
                    : "Kalender über die Handy-App verbinden (⚙︎)", rounded));
            return;
        }
        List<CalendarIcs.Event> next = upcoming(events, now);
        if (next.isEmpty()) {
            list.addView(note(c, events.isEmpty() ? "Keine Termine – genieß den Tag." : "Für heute alles erledigt.", rounded));
            return;
        }
        int rows = next.size() > MAX_ROWS ? MAX_ROWS - 1 : next.size();
        for (int i = 0; i < rows; i++) {
            CalendarIcs.Event e = next.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, i == 0 ? 0 : Math.round(5 * dens), 0, 0);
            TextView time = new TextView(c);
            time.setText(e.allDay ? "●" : clock(e.start));
            time.setTextColor(AbendTile.ORANGE);
            time.setTextSize(e.allDay ? 9 : 14);
            time.setTypeface(rounded);
            time.setIncludeFontPadding(false);
            time.setGravity(Gravity.START);
            boolean now_ = !e.allDay && e.start <= now;
            row.addView(time, new LinearLayout.LayoutParams(Math.round(56 * dens), LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView title = new TextView(c);
            title.setText(e.title.isEmpty() ? "(ohne Titel)" : now_ ? "Jetzt: " + e.title : e.title);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(14);
            title.setTypeface(rounded);
            title.setIncludeFontPadding(false);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            list.addView(row);
        }
        if (rows < next.size()) {
            TextView more = note(c, "+ " + (next.size() - rows) + " weitere", rounded);
            more.setPadding(0, Math.round(6 * dens), 0, 0);
            list.addView(more);
        }
    }

    private static TextView note(Context c, String text, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFFA9ADB5);
        t.setTextSize(13);
        t.setTypeface(tf);
        return t;
    }

    private static String clock(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return String.format(Locale.GERMANY, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /** Zeigt das Feld? morgens = 5 bis 11 Uhr. */
    static boolean visible(String mode, int hour) {
        if ("aus".equals(mode)) return false;
        if ("immer".equals(mode)) return true;
        return hour >= 5 && hour < 11;
    }
}
