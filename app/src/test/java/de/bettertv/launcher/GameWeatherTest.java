package de.bettertv.launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GameWeatherTest {
    @Test public void rainCodes() {
        for (int code : new int[]{51, 61, 65, 80, 82, 95, 99}) assertEquals(Mascot.RAIN, MainActivity.gameWeatherFor(code, 14));
    }

    @Test public void snowCodes() {
        for (int code : new int[]{71, 73, 75, 77, 85, 86}) assertEquals(Mascot.SNOW, MainActivity.gameWeatherFor(code, 14));
    }

    @Test public void sunOnlyDuringTheDay() {
        assertEquals(Mascot.SUN, MainActivity.gameWeatherFor(0, 13));
        assertEquals(Mascot.SUN, MainActivity.gameWeatherFor(1, 9));
        assertEquals(Mascot.CLEAR, MainActivity.gameWeatherFor(0, 21));   // abends keine Sonne
        assertEquals(Mascot.CLEAR, MainActivity.gameWeatherFor(3, 13));   // bedeckt
        assertEquals(Mascot.CLEAR, MainActivity.gameWeatherFor(-1, 13));  // noch kein Wetter
    }
}
