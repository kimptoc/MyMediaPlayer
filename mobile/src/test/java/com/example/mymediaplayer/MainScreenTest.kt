package com.example.mymediaplayer

import android.support.v4.media.session.PlaybackStateCompat
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

    @Test
    fun repeatModeLabel_showsEachMode() {
        assertEquals("Repeat: Off", repeatModeLabel(PlaybackStateCompat.REPEAT_MODE_NONE))
        assertEquals("Repeat: All", repeatModeLabel(PlaybackStateCompat.REPEAT_MODE_ALL))
        assertEquals("Repeat: One", repeatModeLabel(PlaybackStateCompat.REPEAT_MODE_ONE))
        assertEquals("Repeat: All", repeatModeLabel(PlaybackStateCompat.REPEAT_MODE_GROUP))
        assertEquals("Repeat: Off", repeatModeLabel(PlaybackStateCompat.REPEAT_MODE_INVALID))
    }

    @Test
    fun nextRepeatMode_cyclesOffAllOne() {
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL, nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE, nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE, nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE, nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_GROUP))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL, nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_INVALID))
    }

    @Test
    fun isRepeatActive_matchesLabel() {
        listOf(
            PlaybackStateCompat.REPEAT_MODE_INVALID,
            PlaybackStateCompat.REPEAT_MODE_NONE,
            PlaybackStateCompat.REPEAT_MODE_ONE,
            PlaybackStateCompat.REPEAT_MODE_ALL,
            PlaybackStateCompat.REPEAT_MODE_GROUP
        ).forEach { mode ->
            assertEquals(repeatModeLabel(mode) != "Repeat: Off", isRepeatActive(mode))
        }
    }
}
