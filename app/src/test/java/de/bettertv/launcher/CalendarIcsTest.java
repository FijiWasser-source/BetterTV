package de.bettertv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class CalendarIcsTest {
    private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");

    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance(BERLIN);
        c.clear();
        c.set(y, m - 1, d, h, min, 0);
        return c.getTimeInMillis();
    }

    private static List<CalendarIcs.Event> day(String ics, int y, int m, int d) {
        long s = at(y, m, d, 0, 0);
        Calendar c = Calendar.getInstance(BERLIN);
        c.setTimeInMillis(s);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return CalendarIcs.eventsBetween(ics, s, c.getTimeInMillis(), BERLIN);
    }

    private static String cal(String... events) {
        StringBuilder b = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n");
        for (String e : events) b.append("BEGIN:VEVENT\r\n").append(e.replace("\n", "\r\n")).append("\r\nEND:VEVENT\r\n");
        return b.append("END:VCALENDAR\r\n").toString();
    }

    @Test public void singleEventWithTzid() {
        String ics = cal("UID:a\nDTSTART;TZID=Europe/Berlin:20261105T083000\nDTEND;TZID=Europe/Berlin:20261105T093000\nSUMMARY:Zahnarzt");
        List<CalendarIcs.Event> e = day(ics, 2026, 11, 5);
        assertEquals(1, e.size());
        assertEquals("Zahnarzt", e.get(0).title);
        assertEquals(at(2026, 11, 5, 8, 30), e.get(0).start);
        assertEquals(0, day(ics, 2026, 11, 6).size());
    }

    @Test public void utcTimeIsConverted() {
        String ics = cal("UID:a\nDTSTART:20261105T073000Z\nDTEND:20261105T080000Z\nSUMMARY:Call");
        assertEquals(at(2026, 11, 5, 8, 30), day(ics, 2026, 11, 5).get(0).start);
    }

    @Test public void allDayComesFirstAndCoversOnlyItsDay() {
        String ics = cal(
                "UID:b\nDTSTART:20261105T060000Z\nSUMMARY:Früh",
                "UID:a\nDTSTART;VALUE=DATE:20261105\nDTEND;VALUE=DATE:20261106\nSUMMARY:Geburtstag Oma");
        List<CalendarIcs.Event> e = day(ics, 2026, 11, 5);
        assertEquals(2, e.size());
        assertTrue(e.get(0).allDay);
        assertEquals("Geburtstag Oma", e.get(0).title);
        assertEquals(0, day(ics, 2026, 11, 6).size());
        assertEquals(0, day(ics, 2026, 11, 4).size());
    }

    @Test public void weeklyByDayWithExdate() {
        String ics = cal("UID:w\nDTSTART;TZID=Europe/Berlin:20260105T190000\nDTEND;TZID=Europe/Berlin:20260105T200000\n"
                + "RRULE:FREQ=WEEKLY;BYDAY=MO,TH\nEXDATE;TZID=Europe/Berlin:20261105T190000\nSUMMARY:Training");
        assertEquals(1, day(ics, 2026, 11, 2).size());   // Montag
        assertEquals(0, day(ics, 2026, 11, 3).size());   // Dienstag
        assertEquals(0, day(ics, 2026, 11, 5).size());   // Donnerstag, ausgenommen
        assertEquals(1, day(ics, 2026, 11, 12).size());  // nächster Donnerstag
        // Uhrzeit bleibt über die Zeitumstellung gleich
        assertEquals(at(2026, 11, 12, 19, 0), day(ics, 2026, 11, 12).get(0).start);
    }

    @Test public void countAndUntilEndSeries() {
        String ics = cal(
                "UID:c\nDTSTART;TZID=Europe/Berlin:20261101T100000\nRRULE:FREQ=DAILY;COUNT=3\nSUMMARY:Kurs",
                "UID:u\nDTSTART;TZID=Europe/Berlin:20261101T120000\nRRULE:FREQ=DAILY;INTERVAL=2;UNTIL=20261105T235959Z\nSUMMARY:Gießen");
        assertEquals(2, day(ics, 2026, 11, 3).size());   // Kurs (3.) + Gießen (1.,3.,5.)
        assertEquals(1, day(ics, 2026, 11, 5).size());   // nur Gießen
        assertEquals(0, day(ics, 2026, 11, 4).size());
        assertEquals(0, day(ics, 2026, 11, 7).size());
    }

    @Test public void monthlyAndYearly() {
        String ics = cal(
                "UID:m\nDTSTART;VALUE=DATE:20250315\nRRULE:FREQ=MONTHLY\nSUMMARY:Miete",
                "UID:y\nDTSTART;VALUE=DATE:19900705\nRRULE:FREQ=YEARLY\nSUMMARY:Geburtstag");
        assertEquals("Miete", day(ics, 2026, 11, 15).get(0).title);
        assertEquals("Geburtstag", day(ics, 2027, 7, 5).get(0).title);
        assertEquals(0, day(ics, 2026, 11, 16).size());
    }

    @Test public void foldedLinesAndEscapes() {
        String ics = cal("UID:f\nDTSTART:20261105T090000Z\nSUMMARY:Treffen mit Anna\\, Ben \n und Carl\\; danach Kaffee");
        assertEquals("Treffen mit Anna, Ben und Carl; danach Kaffee", day(ics, 2026, 11, 5).get(0).title);
    }

    @Test public void cancelledIsSkipped() {
        String ics = cal("UID:x\nDTSTART:20261105T090000Z\nSTATUS:CANCELLED\nSUMMARY:Fällt aus");
        assertEquals(0, day(ics, 2026, 11, 5).size());
    }

    @Test public void movedOccurrenceReplacesOriginal() {
        String ics = cal(
                "UID:r\nDTSTART;TZID=Europe/Berlin:20261102T090000\nRRULE:FREQ=DAILY\nSUMMARY:Standup",
                "UID:r\nRECURRENCE-ID;TZID=Europe/Berlin:20261105T090000\nDTSTART;TZID=Europe/Berlin:20261106T110000\nSUMMARY:Standup (verschoben)");
        assertEquals(0, day(ics, 2026, 11, 5).size());
        List<CalendarIcs.Event> fri = day(ics, 2026, 11, 6);
        assertEquals(2, fri.size());
        assertEquals("Standup", fri.get(0).title);
        assertEquals("Standup (verschoben)", fri.get(1).title);
    }
}
