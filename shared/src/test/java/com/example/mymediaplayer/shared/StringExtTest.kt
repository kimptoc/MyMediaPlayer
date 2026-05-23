package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class StringExtTest {
    @Test
    fun fileExtension_extractsCorrectly() {
        assertEquals(".mp3", "file.mp3".fileExtension())
        assertEquals("(none)", "no_ext".fileExtension())
        assertEquals("(none)", "file.".fileExtension())
        assertEquals(".hidden", ".hidden".fileExtension())
        assertEquals(".jpg", "file.NAME.JPG".fileExtension())
        assertEquals(".mp4", "some/path.to/file.mp4".fileExtension())
    }
}
