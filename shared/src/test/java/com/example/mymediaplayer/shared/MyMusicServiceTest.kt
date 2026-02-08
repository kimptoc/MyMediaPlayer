package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MyMusicServiceTest {

    @Test
    fun buildSongListItems_withSongsAddsPlayAndShuffle() {
        val songs = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "Song One"
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song Two",
                sizeBytes = 1L,
                title = "Song Two"
            )
        )

        val items = buildSongListItems(songs, "album:Rock")

        assertEquals(4, items.size)
        assertEquals("action:play_all:album:Rock", items[0].description.mediaId)
        assertEquals("[Play All]", items[0].description.title)
        assertEquals("action:shuffle:album:Rock", items[1].description.mediaId)
        assertEquals("[Shuffle]", items[1].description.title)
        assertEquals(songs[0].uriString, items[2].description.mediaId)
        assertEquals(songs[1].uriString, items[3].description.mediaId)
    }

    @Test
    fun buildSongListItems_emptyHasNoActions() {
        val items = buildSongListItems(emptyList(), "album:Rock")

        assertTrue(items.isEmpty())
    }

    @Test
    fun buildCategoryListItems_appliesCountsToLabels() {
        val categories = listOf("Rock", "Pop")
        val counts = mapOf("Rock" to 2)

        val items = buildCategoryListItems(categories, "genre:", counts)

        assertEquals(2, items.size)
        assertEquals("genre:Rock", items[0].description.mediaId)
        assertEquals("Rock (2)", items[0].description.title)
        assertEquals("genre:Pop", items[1].description.mediaId)
        assertEquals("Pop", items[1].description.title)
    }

    @Test
    fun advanceQueueOnError_advancesQueueWhenPossible() {
        val songs = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "Song One"
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song Two",
                sizeBytes = 1L,
                title = "Song Two"
            )
        )

        val nextIndex = nextQueueIndexForError(currentIndex = 0, queueSize = songs.size)
        assertEquals(1, nextIndex)
    }

    @Test
    fun shouldLoadChildrenAsync_requiresIndexes() {
        val service = MyMusicService()

        assertTrue(service.shouldLoadChildrenAsync("albums", hasIndexes = false))
        assertTrue(service.shouldLoadChildrenAsync("album:Rock", hasIndexes = false))
        assertTrue(service.shouldLoadChildrenAsync("genres", hasIndexes = false))
        assertTrue(service.shouldLoadChildrenAsync("artist_letter:A", hasIndexes = false))
        assertTrue(service.shouldLoadChildrenAsync("genre_letter:R", hasIndexes = false))
        assertTrue(service.shouldLoadChildrenAsync("decades", hasIndexes = false))
        assertTrue(service.shouldLoadChildrenAsync("decade:1990s", hasIndexes = false))

        assertFalse(service.shouldLoadChildrenAsync("songs", hasIndexes = false))
        assertFalse(service.shouldLoadChildrenAsync("search", hasIndexes = false))
        assertFalse(service.shouldLoadChildrenAsync("albums", hasIndexes = true))
    }
}
