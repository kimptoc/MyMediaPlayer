package com.example.mymediaplayer

import org.junit.Test
import org.junit.Assert.assertEquals

class MainScreenTest {

    @Test
    fun formatPlaybackDuration_zeroMs() {
        assertEquals("0:00", formatPlaybackDuration(0L))
    }

    @Test
    fun formatPlaybackDuration_lessThanOneSecond() {
        assertEquals("0:00", formatPlaybackDuration(999L))
    }

    @Test
    fun formatPlaybackDuration_exactlyOneSecond() {
        assertEquals("0:01", formatPlaybackDuration(1000L))
    }

    @Test
    fun formatPlaybackDuration_lessThanOneMinute() {
        assertEquals("0:59", formatPlaybackDuration(59000L))
    }

    @Test
    fun formatPlaybackDuration_exactlyOneMinute() {
        assertEquals("1:00", formatPlaybackDuration(60000L))
    }

    @Test
    fun formatPlaybackDuration_moreThanOneMinute() {
        assertEquals("1:05", formatPlaybackDuration(65000L))
    }

    @Test
    fun formatPlaybackDuration_moreThanTenMinutes() {
        assertEquals("10:05", formatPlaybackDuration(605000L))
    }

    @Test
    fun formatPlaybackDuration_moreThanOneHour() {
        // 60 minutes * 60 seconds = 3600 seconds = 3600000 ms
        // 1 hour, 1 minute, 1 second = 3600 + 60 + 1 = 3661 seconds = 3661000 ms
        assertEquals("61:01", formatPlaybackDuration(3661000L))
    }

    @Test
    fun formatPlaybackDuration_negativeMs() {
        // coerceAtLeast(0L) should handle this
        assertEquals("0:00", formatPlaybackDuration(-1000L))
    }
}
