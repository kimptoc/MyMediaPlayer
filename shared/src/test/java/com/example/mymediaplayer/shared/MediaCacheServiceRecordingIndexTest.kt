package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCacheServiceRecordingIndexTest {

    private val song = MediaFileInfo(
        uriString = "file:///Music/Band/Album/song.mp3",
        displayName = "song.mp3",
        sizeBytes = 1L,
        title = "Song",
        artist = "Band",
        album = "Album",
        genre = "Rock",
        year = 1985
    )
    private val recording = MediaFileInfo(
        uriString = "file:///iPhoneData/Sounds/New Recording.m4a",
        displayName = "New Recording.m4a",
        sizeBytes = 1L,
        title = "New Recording",
        album = "Sounds",
        year = 2020,
        isRecording = true
    )

    @Test
    fun recordingsLandOnlyInRecordingsGenre() {
        val service = MediaCacheService()
        service.addAllFiles(listOf(song, recording))

        service.buildAlbumArtistIndexesFromCache()

        assertEquals(listOf(recording), service.songsForGenre(RECORDINGS_GENRE))
        assertFalse("recording album hidden", service.albums().contains("Sounds"))
        assertFalse("recording decade hidden", service.decades().contains("2020s"))
        assertTrue(service.albums().contains("Album"))
    }

    @Test
    fun cachedMusicFiles_leaveOutRecordings() {
        val service = MediaCacheService()
        service.addAllFiles(listOf(song, recording))

        assertEquals(listOf(song), service.cachedMusicFiles)
    }

    @Test
    fun libraryGenreOf_namesEachKind() {
        assertEquals(RECORDINGS_GENRE, libraryGenreOf(recording))
        assertEquals(PODCAST_GENRE, libraryGenreOf(song.copy(isPodcast = true)))
        assertEquals(bucketGenre("Rock"), libraryGenreOf(song))
    }
}
