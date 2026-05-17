package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFileInfoTest {

    @Test
    fun cleanTitle_hasTitle_returnsTitle() {
        val info = MediaFileInfo(
            uriString = "uri",
            displayName = "file.mp3",
            sizeBytes = 100L,
            title = "My Song Title"
        )
        assertEquals("My Song Title", info.cleanTitle)
    }

    @Test
    fun cleanTitle_blankTitle_returnsDisplayNameWithoutExtension() {
        val info = MediaFileInfo(
            uriString = "uri",
            displayName = "file.name.with.dots.mp3",
            sizeBytes = 100L,
            title = "   "
        )
        assertEquals("file.name.with.dots", info.cleanTitle)
    }

    @Test
    fun cleanTitle_nullTitle_returnsDisplayNameWithoutExtension() {
        val info = MediaFileInfo(
            uriString = "uri",
            displayName = "song.m4a",
            sizeBytes = 100L,
            title = null
        )
        assertEquals("song", info.cleanTitle)
    }

    @Test
    fun cleanTitle_nullTitleNoExtension_returnsFullDisplayName() {
        val info = MediaFileInfo(
            uriString = "uri",
            displayName = "song_without_extension",
            sizeBytes = 100L,
            title = null
        )
        assertEquals("song_without_extension", info.cleanTitle)
    }
}
