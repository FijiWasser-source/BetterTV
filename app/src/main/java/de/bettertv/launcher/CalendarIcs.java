package de.bettertv.launcher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Liest Termine aus einem iCal-Kalender (z. B. Google Kalender "Privatadresse im iCal-Format").
 * Unterstützt: Einzeltermine, ganztägige Termine, Zeitzonen (TZID / UTC / lokal), Serien
 * (RRULE: DAILY, WEEKLY mit BYDAY, MONTHLY, YEARLY; INTERVAL, COUNT, UNTIL), EXDATE,
 * geänderte Einzeltermine (RECURRENCE-ID) und abgesagte Termine (STATUS:CANCELLED).
 * Ohne Android-Abhängigkeiten, damit es testbar ist.
 */
final class CalendarIcs {
    private CalendarIcs() {}

    static final class Event implements Comparable<Event> {
        final long start, end; final boolean allDay; final String title;
        Event(long start, long end, boolean allDay, String title) { this.start = start; this.end = end; this.allDay = allDay; this.title = title; }
        @Override public int compareTo(Event o) {
            if (allDay != o.allDay) return allDay ? -1 : 1;     // ganztägige zuerst
            return Long.compare(start, o.start);
        }
    }

    /** Ein Zeitwert aus der Datei: Millisekunden + ob nur Datum. */
    private static final class Stamp { long millis; boolean dateOnly; }

    private static final class VEvent {
        String uid = "", summary = "", rrule, status = "";
        Stamp start, end, recurrenceId;
        final List<Long> exdates = new ArrayList<>();
    }

    /** Termine, die den Tag [dayStart, dayEnd) berühren, sortiert. */
    static List<Event> eventsBetween(String ics, long dayStart, long dayEnd, TimeZone local) {
        List<VEvent> all = parse(ics, local);
        // geänderte Einzeltermine: UID + ursprüngliche Startzeit
        Set<String> overridden = new HashSet<>();
        for (VEvent v : all) if (v.recurrenceId != null) overridden.add(v.uid + "@" + v.recurrenceId.millis);
        List<Event> out = new ArrayList<>();
        for (VEvent v : all) {
            if (v.start == null || "CANCELLED".equalsIgnoreCase(v.status)) continue;
            long duration = v.end != null ? Math.max(0, v.end.millis - v.start.millis) : (v.start.dateOnly ? 86_400_000L : 0);
            if (v.rrule == null || v.recurrenceId != null) {
                addIfOverlaps(out, v, v.start.millis, duration, dayStart, dayEnd);
                continue;
            }
            for (long occ : occurrences(v, local, dayStart - 7 * DAY, dayEnd)) {
                if (v.exdates.contains(occ) || overridden.contains(v.uid + "@" + occ)) continue;
                addIfOverlaps(out, v, occ, duration, dayStart, dayEnd);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static final long DAY = 86_400_000L;

    private static void addIfOverlaps(List<Event> out, VEvent v, long s, long dur, long dayStart, long dayEnd) {
        long e = s + Math.max(dur, 1);
        if (s < dayEnd && e > dayStart) out.add(new Event(s, s + dur, v.start.dateOnly, v.summary));
    }

    // ---------- Serien ----------

    private static List<Long> occurrences(VEvent v, TimeZone local, long from, long until) {
        Map<String, String> r = new HashMap<>();
        for (String part : v.rrule.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) r.put(part.substring(0, eq).toUpperCase(Locale.ROOT), part.substring(eq + 1));
        }
        String freq = r.containsKey("FREQ") ? r.get("FREQ").toUpperCase(Locale.ROOT) : "";
        int interval = 1;
        try { if (r.containsKey("INTERVAL")) interval = Math.max(1, Integer.parseInt(r.get("INTERVAL"))); } catch (NumberFormatException ignored) {}
        int count = Integer.MAX_VALUE;
        try { if (r.containsKey("COUNT")) count = Integer.parseInt(r.get("COUNT")); } catch (NumberFormatException ignored) {}
        long ruleUntil = Long.MAX_VALUE;
        if (r.containsKey("UNTIL")) { Stamp u = parseStamp(r.get("UNTIL"), null, local); if (u != null) ruleUntil = u.dateOnly ? u.millis + 86_399_999L : u.millis; }
        long limit = Math.min(until, ruleUntil);

        List<Long> out = new ArrayList<>();
        Calendar c = Calendar.getInstance(local);
        c.setTimeInMillis(v.start.millis);
        int produced = 0;
        if ("WEEKLY".equals(freq) && r.containsKey("BYDAY")) {
            int[] days = weekdays(r.get("BYDAY"));
            Calendar week = (Calendar) c.clone();
            if (count == Integer.MAX_VALUE && from > v.start.millis) {   // alte Serien: vorspulen
                long skip = (from - v.start.millis) / (7 * DAY * interval) - 1;
                if (skip > 0) week.add(Calendar.WEEK_OF_YEAR, (int) (skip * interval));
            }
            // auf Montag der Startwoche zurück
            int dow = week.get(Calendar.DAY_OF_WEEK);
            week.add(Calendar.DAY_OF_MONTH, -((dow + 5) % 7));
            for (int guard = 0; guard < 3000 && produced < count; guard++) {
                for (int d : days) {
                    Calendar inst = (Calendar) week.clone();
                    inst.add(Calendar.DAY_OF_MONTH, (d + 5) % 7);   // Mo=0 … So=6
                    long t = inst.getTimeInMillis();
                    if (t < v.start.millis) continue;
                    if (t > limit) return out;
                    out.add(t); produced++;
                    if (produced >= count) return out;
                }
                week.add(Calendar.WEEK_OF_YEAR, interval);
                if (week.getTimeInMillis() > limit) break;
            }
            return out;
        }
        int field;
        switch (freq) {
            case "DAILY": field = Calendar.DAY_OF_MONTH; break;
            case "WEEKLY": field = Calendar.WEEK_OF_YEAR; break;
            case "MONTHLY": field = Calendar.MONTH; break;
            case "YEARLY": field = Calendar.YEAR; break;
            default: out.add(v.start.millis); return out;
        }
        Calendar base = (Calendar) c.clone();
        int first = 0;
        if (count == Integer.MAX_VALUE && from > v.start.millis) {   // alte Serien: vorspulen
            long period = field == Calendar.DAY_OF_MONTH ? DAY : field == Calendar.WEEK_OF_YEAR ? 7 * DAY
                    : field == Calendar.MONTH ? 31 * DAY : 366 * DAY;
            first = (int) Math.max(0, (from - v.start.millis) / (period * interval) - 2);
        }
        for (int i = first; i < first + 20000 && produced < count; i++) {
            Calendar inst = (Calendar) base.clone();
            inst.add(field, i * interval);   // vom Start aus rechnen (31. bleibt sauber)
            long t = inst.getTimeInMillis();
            if (t > limit) break;
            out.add(t); produced++;
        }
        return out;
    }

    private static int[] weekdays(String byday) {
        String[] parts = byday.split(",");
        List<Integer> days = new ArrayList<>();
        for (String p : parts) {
            String d = p.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
            switch (d) {
                case "MO": days.add(Calendar.MONDAY); break;
                case "TU": days.add(Calendar.TUESDAY); break;
                case "WE": days.add(Calendar.WEDNESDAY); break;
                case "TH": days.add(Calendar.THURSDAY); break;
                case "FR": days.add(Calendar.FRIDAY); break;
                case "SA": days.add(Calendar.SATURDAY); break;
                case "SU": days.add(Calendar.SUNDAY); break;
                default: break;
            }
        }
        // in Wochenreihenfolge Mo..So
        Collections.sort(days, (a, b) -> Integer.compare((a + 5) % 7, (b + 5) % 7));
        int[] out = new int[days.size()];
        for (int i = 0; i < out.length; i++) out[i] = days.get(i);
        return out;
    }

    // ---------- Einlesen ----------

    private static List<VEvent> parse(String ics, TimeZone local) {
        List<VEvent> events = new ArrayList<>();
        if (ics == null) return events;
        // gefaltete Zeilen (Fortsetzung beginnt mit Leerzeichen/Tab) zusammenfügen
        String[] raw = ics.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> lines = new ArrayList<>();
        for (String l : raw) {
            if (!lines.isEmpty() && (l.startsWith(" ") || l.startsWith("\t"))) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + l.substring(1));
            } else lines.add(l);
        }
        VEvent cur = null;
        for (String line : lines) {
            if (line.equals("BEGIN:VEVENT")) { cur = new VEvent(); continue; }
            if (line.equals("END:VEVENT")) { if (cur != null) events.add(cur); cur = null; continue; }
            if (cur == null) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String head = line.substring(0, colon), value = line.substring(colon + 1);
            String name = head, tzid = null;
            int semi = head.indexOf(';');
            if (semi >= 0) {
                name = head.substring(0, semi);
                for (String param : head.substring(semi + 1).split(";")) {
                    if (param.toUpperCase(Locale.ROOT).startsWith("TZID=")) tzid = param.substring(5).replace("\"", "");
                }
            }
            switch (name.toUpperCase(Locale.ROOT)) {
                case "UID": cur.uid = value; break;
                case "SUMMARY": cur.summary = unescape(value); break;
                case "STATUS": cur.status = value.trim(); break;
                case "RRULE": cur.rrule = value; break;
                case "DTSTART": cur.start = parseStamp(value, tzid, local); break;
                case "DTEND": cur.end = parseStamp(value, tzid, local); break;
                case "RECURRENCE-ID": cur.recurrenceId = parseStamp(value, tzid, local); break;
                case "EXDATE":
                    for (String v : value.split(",")) { Stamp s = parseStamp(v, tzid, local); if (s != null) cur.exdates.add(s.millis); }
                    break;
                default: break;
            }
        }
        return events;
    }

    /** 20261105, 20261105T083000, 20261105T073000Z – mit optionaler TZID. */
    private static Stamp parseStamp(String v, String tzid, TimeZone local) {
        v = v.trim();
        try {
            Stamp s = new Stamp();
            int y = Integer.parseInt(v.substring(0, 4)), mo = Integer.parseInt(v.substring(4, 6)), d = Integer.parseInt(v.substring(6, 8));
            TimeZone tz = local;
            int h = 0, mi = 0, se = 0;
            if (v.length() >= 15 && v.charAt(8) == 'T') {
                h = Integer.parseInt(v.substring(9, 11)); mi = Integer.parseInt(v.substring(11, 13)); se = Integer.parseInt(v.substring(13, 15));
                if (v.endsWith("Z")) tz = TimeZone.getTimeZone("UTC");
                else if (tzid != null) tz = TimeZone.getTimeZone(tzid);
            } else {
                s.dateOnly = true; // ganztägig: lokaler Tag
            }
            Calendar c = Calendar.getInstance(tz);
            c.clear();
            c.set(y, mo - 1, d, h, mi, se);
            s.millis = c.getTimeInMillis();
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String unescape(String s) {
        return s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim();
    }
}
