package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCacheServiceSearchFilesTest {

    private lateinit var service: MediaCacheService

    private val file1 = MediaFileInfo(
        uriString = "uri1",
        displayName = "song1.mp3",
        sizeBytes = 100,
        title = "Summer Vibes",
        artist = "The Beach Boys",
        album = "Greatest Hits",
        genre = "Pop"
    )

    private val file2 = MediaFileInfo(
        uriString = "uri2",
        displayName = "song2.mp3",
        sizeBytes = 200,
        title = "Winter Chill",
        artist = "Cool Dude",
        album = "Snowy Days",
        genre = "Lo-Fi"
    )

    private val file3 = MediaFileInfo(
        uriString = "uri3",
        displayName = "song_no_metadata.mp3",
        sizeBytes = 300,
        title = null,
        artist = null,
        album = null,
        genre = null
    )

    @Before
    fun setup() {
        service = MediaCacheService()
        service.addAllFiles(listOf(file1, file2, file3))
    }

    @Test
    fun searchFiles_withBlankQuery_returnsEmptyList() {
        val results = service.searchFiles("   ")
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchFiles_matchesTitleCaseInsensitive() {
        val results = service.searchFiles("SuMmEr")
        assertEquals(1, results.size)
        assertEquals(file1, results[0])
    }

    @Test
    fun searchFiles_matchesArtist() {
        val results = service.searchFiles("cool")
        assertEquals(1, results.size)
        assertEquals(file2, results[0])
    }

    @Test
    fun searchFiles_matchesAlbum() {
        val results = service.searchFiles("greatest")
        assertEquals(1, results.size)
        assertEquals(file1, results[0])
    }

    @Test
    fun searchFiles_matchesGenre() {
        val results = service.searchFiles("Lo-Fi")
        assertEquals(1, results.size)
        assertEquals(file2, results[0])
    }

    @Test
    fun searchFiles_matchesCleanTitleWhenNoMetadata() {
        val results = service.searchFiles("song_no_metadata")
        assertEquals(1, results.size)
        assertEquals(file3, results[0])
    }

    @Test
    fun searchFiles_matchesAcrossMultipleFields() {
        val results = service.searchFiles("Vibes The")
        assertEquals(1, results.size)
        assertEquals(file1, results[0])
    }

    @Test
    fun searchFiles_noMatch_returnsEmptyList() {
        val results = service.searchFiles("NonExistent")
        assertTrue(results.isEmpty())
    }
}
