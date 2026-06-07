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

    @Test
    fun fileExtension_edgeCases() {
        // Empty and basic
        assertEquals("(none)", "".fileExtension())
        assertEquals("(none)", ".".fileExtension())
        assertEquals("(none)", "..".fileExtension())

        // Multiple dots
        assertEquals(".txt", ".hidden.txt".fileExtension())
        assertEquals(".d", "a.b.c.d".fileExtension())

        // Paths
        assertEquals("(none)", "some.dir/file".fileExtension())
        assertEquals("(none)", "some.dir/file.".fileExtension())
        assertEquals(".txt", "some.dir/file.txt".fileExtension())
        assertEquals(".gz", "archive.tar.gz".fileExtension())

        // Backslash paths (Windows-style)
        assertEquals("(none)", "some.dir\\file".fileExtension())
        assertEquals(".txt", "some.dir\\file.txt".fileExtension())
        assertEquals(".mp3", "C:\\Music\\some.album\\track.mp3".fileExtension())
    }
}
