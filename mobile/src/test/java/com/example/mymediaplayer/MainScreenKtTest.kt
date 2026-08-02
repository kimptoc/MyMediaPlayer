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

    @Test
    fun getPlaylistCountValidation_zeroMaxCount() {
        val (isValid, helperText) = getPlaylistCountValidation(maxCount = 0, playlistCountText = "5")
        org.junit.Assert.assertFalse(isValid)
        assertEquals("Scan a folder to enable playlists.", helperText)
    }

    @Test
    fun getPlaylistCountValidation_nonIntegerInput() {
        val (isValid, helperText) = getPlaylistCountValidation(maxCount = 10, playlistCountText = "abc")
        org.junit.Assert.assertFalse(isValid)
        assertEquals("Enter a number between 1 and 10.", helperText)
    }

    @Test
    fun getPlaylistCountValidation_tooSmallInput() {
        val (isValid, helperText) = getPlaylistCountValidation(maxCount = 10, playlistCountText = "0")
        org.junit.Assert.assertFalse(isValid)
        assertEquals("Enter a number between 1 and 10.", helperText)
    }

    @Test
    fun getPlaylistCountValidation_tooLargeInput() {
        val (isValid, helperText) = getPlaylistCountValidation(maxCount = 10, playlistCountText = "11")
        org.junit.Assert.assertFalse(isValid)
        assertEquals("Enter a number between 1 and 10.", helperText)
    }

    @Test
    fun getPlaylistCountValidation_validInput() {
        val (isValid, helperText) = getPlaylistCountValidation(maxCount = 10, playlistCountText = "5")
        org.junit.Assert.assertTrue(isValid)
        assertEquals("OK", helperText)
    }
}
