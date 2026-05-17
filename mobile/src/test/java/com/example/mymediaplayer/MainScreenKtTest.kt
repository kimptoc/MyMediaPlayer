package com.example.mymediaplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenKtTest {

    @Test
    fun formatDuration_handlesNegativeDuration() {
        assertEquals("", formatDuration(-1000L))
    }

    @Test
    fun formatDuration_handlesZeroDuration() {
        assertEquals("", formatDuration(0L))
    }

    @Test
    fun formatDuration_formatsSecondsOnly() {
        assertEquals("0:45", formatDuration(45000L))
    }

    @Test
    fun formatDuration_formatsExactMinute() {
        assertEquals("1:00", formatDuration(60000L))
    }

    @Test
    fun formatDuration_formatsMinutesAndSeconds() {
        assertEquals("1:05", formatDuration(65000L))
        assertEquals("3:45", formatDuration(225000L))
    }

    @Test
    fun formatDuration_formatsMoreThanAnHour() {
        // 60 minutes + 5 minutes + 10 seconds = 65 minutes 10 seconds => 3910 seconds = 3910000L
        assertEquals("65:10", formatDuration(3910000L))
    }
}
