package com.example.mymediaplayer.shared

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun buildSongItems_noActionsAdded() {
        val songs = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "Song One"
            )
        )

        val items = buildSongItems(songs)

        assertEquals(1, items.size)
        assertEquals(songs[0].uriString, items[0].description.mediaId)
        assertEquals("Song One", items[0].description.title)
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
    fun buildSongLetterBuckets_generatesSortedBucketsWithHashLast() {
        val service = MyMusicService()
        val songs = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Alpha",
                sizeBytes = 1L,
                title = "Alpha"
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "beta",
                sizeBytes = 1L,
                title = "beta"
            ),
            MediaFileInfo(
                uriString = "content://test/song3",
                displayName = "123",
                sizeBytes = 1L,
                title = "123"
            )
        )

        val buckets = service.buildSongLetterBuckets(songs)

        assertEquals(listOf("A", "B", "#"), buckets)
    }

    @Test
    fun buildSongLetterCounts_countsLettersAndOther() {
        val service = MyMusicService()
        val songs = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Alpha",
                sizeBytes = 1L,
                title = "Alpha"
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Another",
                sizeBytes = 1L,
                title = "Another"
            ),
            MediaFileInfo(
                uriString = "content://test/song3",
                displayName = "beta",
                sizeBytes = 1L,
                title = "beta"
            ),
            MediaFileInfo(
                uriString = "content://test/song4",
                displayName = "123",
                sizeBytes = 1L,
                title = "123"
            )
        )

        val counts = service.buildSongLetterCounts(songs)

        assertEquals(2, counts["A"])
        assertEquals(1, counts["B"])
        assertEquals(1, counts["#"])
    }

    @Test
    fun parseBucketParts_decodesValidPayload() {
        val service = MyMusicService()
        val genre = "Heavy Metal"
        val letter = "#"
        val encoded = "genre_song_letter:" +
            Uri.encode(genre) + ":" + Uri.encode(letter)

        val parts = service.parseBucketParts(encoded, "genre_song_letter:")

        assertEquals(genre, parts?.first)
        assertEquals(letter, parts?.second)
    }

    @Test
    fun parseBucketParts_returnsNullForInvalidPayload() {
        val service = MyMusicService()

        val parts = service.parseBucketParts("genre_song_letter:Rock", "genre_song_letter:")

        assertNull(parts)
    }

    @Test
    fun formatBucketTitle_formatsPairOrReturnsNull() {
        val service = MyMusicService()
        val encoded = "decade_song_letter:" + Uri.encode("1990s") + ":" + Uri.encode("C")

        assertEquals("1990s • C", service.formatBucketTitle(encoded, "decade_song_letter:"))
        assertNull(service.formatBucketTitle("decade_song_letter:1990s", "decade_song_letter:"))
    }

    @Test
    fun shouldBucketSongs_usesThreshold() {
        val service = MyMusicService()

        assertFalse(service.shouldBucketSongs(500))
        assertTrue(service.shouldBucketSongs(501))
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
        assertTrue(service.shouldLoadChildrenAsync("genre_song_letter:Rock:C", hasIndexes = false))
        assertTrue(service.shouldLoadChildrenAsync("decade_song_letter:1990s:C", hasIndexes = false))

        assertFalse(service.shouldLoadChildrenAsync("songs", hasIndexes = false))
        assertFalse(service.shouldLoadChildrenAsync("search", hasIndexes = false))
        assertFalse(service.shouldLoadChildrenAsync("albums", hasIndexes = true))
    }
}
