package com.sailracing.app.ui.format

import org.junit.Test
import kotlin.test.assertEquals

class FormattersTest {

    @Test
    fun countdownRoundsUpBeforeTheStartAndShowsElapsedAfter() {
        assertEquals("05:00", Formatters.countdown(300_000))
        assertEquals("04:60".let { "05:00" }, Formatters.countdown(299_001))
        assertEquals("04:59", Formatters.countdown(299_000))
        assertEquals("00:01", Formatters.countdown(1))
        assertEquals("00:00", Formatters.countdown(0))
        assertEquals("00:00", Formatters.countdown(-999))
        assertEquals("+00:01", Formatters.countdown(-1_000))
        assertEquals("+01:12", Formatters.countdown(-72_500))
        assertEquals("1:01:05", Formatters.countdown(3_665_000))
        assertEquals("+1:00:00", Formatters.countdown(-3_600_000))
    }

    @Test
    fun timeOfDayUsesTheGivenOffset() {
        assertEquals("00:00", Formatters.timeOfDay(0, zoneOffsetMillis = 0))
        assertEquals("13:07", Formatters.timeOfDay((13 * 3600 + 7 * 60) * 1000L, zoneOffsetMillis = 0))
        assertEquals("14:07", Formatters.timeOfDay((13 * 3600 + 7 * 60) * 1000L, zoneOffsetMillis = 3_600_000))
        assertEquals("23:30", Formatters.timeOfDay(0, zoneOffsetMillis = -30 * 60_000))
    }

    @Test
    fun secondsAndDegreesCarrySigns() {
        assertEquals("+12 s", Formatters.signedSeconds(12.4))
        assertEquals("-4 s", Formatters.signedSeconds(-3.6))
        assertEquals("0 s", Formatters.signedSeconds(0.2))
        assertEquals("7 s", Formatters.seconds(7.3))
        assertEquals("+4°", Formatters.signedDegrees(4.2))
        assertEquals("-12°", Formatters.signedDegrees(-12.0))
        assertEquals("0°", Formatters.signedDegrees(-0.3))
        assertEquals("+0.3 kn", Formatters.signedKnots(0.15))
        assertEquals("-1.2 kn", Formatters.signedKnots(-0.6))
        assertEquals("0.0 kn", Formatters.signedKnots(-0.01))
        assertEquals("005°", Formatters.degrees(5.0))
        assertEquals("000°", Formatters.degrees(359.6))
        assertEquals("359°", Formatters.degrees(-1))
    }

    @Test
    fun distancesAndSpeeds() {
        assertEquals("85 m", Formatters.meters(84.6))
        assertEquals("1.2 km", Formatters.meters(1_234.0))
        assertEquals("5.8 kn", Formatters.knots(3.0))
        assertEquals("5.8", Formatters.knotsValue(3.0))
        assertEquals(3.0, Formatters.knotsToMetersPerSecond(5.831532), 1e-5)
        assertEquals("---", Formatters.PLACEHOLDER)
    }
}
