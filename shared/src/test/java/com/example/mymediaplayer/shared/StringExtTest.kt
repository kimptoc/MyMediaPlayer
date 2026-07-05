package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StringExtTest(private val input: String, private val expectedExt: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                // Basic cases
                arrayOf("file.mp3", ".mp3"),
                arrayOf("no_ext", "(none)"),
                arrayOf("file.", "(none)"),
                arrayOf(".hidden", ".hidden"),
                arrayOf("file.NAME.JPG", ".jpg"),
                arrayOf("some/path.to/file.mp4", ".mp4"),

                // Empty and basic
                arrayOf("", "(none)"),
                arrayOf(".", "(none)"),
                arrayOf("..", "(none)"),

                // Multiple dots
                arrayOf(".hidden.txt", ".txt"),
                arrayOf("a.b.c.d", ".d"),

                // Paths
                arrayOf("some.dir/file", "(none)"),
                arrayOf("some.dir/file.", "(none)"),
                arrayOf("some.dir/file.txt", ".txt"),
                arrayOf("archive.tar.gz", ".gz"),

                // Backslash paths (Windows-style)
                arrayOf("some.dir\\file", "(none)"),
                arrayOf("some.dir\\file.txt", ".txt"),
                arrayOf("C:\\Music\\some.album\\track.mp3", ".mp3"),

                // Additional Edge Cases
                arrayOf(" ", "(none)"),
                arrayOf(" / ", "(none)"),
                arrayOf("file. txt ", ". txt "),
                arrayOf("folder.ext/file", "(none)"),
                arrayOf("folder.ext\\file", "(none)"),
                arrayOf("🌟.🎵", ".🎵"),
                arrayOf("file.ext/", "(none)"),
                arrayOf("file.ext\\", "(none)"),
                arrayOf("/var/log/messages.1", ".1")
            )
        }
    }

    @Test
    fun fileExtension_extractsCorrectly() {
        assertEquals(expectedExt, input.fileExtension())
    }
}
