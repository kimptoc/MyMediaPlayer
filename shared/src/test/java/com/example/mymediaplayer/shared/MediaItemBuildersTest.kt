package com.example.mymediaplayer.shared

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemBuildersTest {

    @Test
    fun buildSongListItems_emptySongs_returnsEmptyList() {
        val result = buildSongListItems(emptyList(), "test_key")
        assertEquals(0, result.size)
    }

    @Test
    fun buildSongItems_mapsFileInfoToMediaItem() {
        val songs = listOf(
            MediaFileInfo("uri1", "Song1.mp3", 1000L, title = "Title1", artist = "Artist1"),
            MediaFileInfo("uri2", "Song2.mp3", 2000L, title = "Title2", artist = "Artist2")
        )
        val defaultIconUri = Uri.parse("content://default_icon")

        val result = buildSongItems(songs, defaultIconUri)

        assertEquals(2, result.size)

        assertEquals("uri1", result[0].mediaId)
        assertEquals("Title1", result[0].description.title)
        assertEquals("Artist1", result[0].description.subtitle)
        assertEquals(defaultIconUri, result[0].description.iconUri)
        assertEquals(MediaItem.FLAG_PLAYABLE, result[0].flags)

        assertEquals("uri2", result[1].mediaId)
        assertEquals("Title2", result[1].description.title)
        assertEquals("Artist2", result[1].description.subtitle)
        assertEquals(defaultIconUri, result[1].description.iconUri)
        assertEquals(MediaItem.FLAG_PLAYABLE, result[1].flags)
    }

    @Test
    fun buildSongItems_withoutDefaultIconUri_doesNotSetIconUri() {
        val songs = listOf(
            MediaFileInfo("uri1", "Song1.mp3", 1000L, title = "Title1", artist = "Artist1")
        )

        val result = buildSongItems(songs)

        assertEquals(1, result.size)
        assertNull(result[0].description.iconUri)
    }

    @Test
    fun buildSongListItems_withSongs_addsPlayAllAndShuffleAndSongs() {
        val songs = listOf(
            MediaFileInfo("uri1", "Song1.mp3", 1000L, title = "Title1", artist = "Artist1"),
            MediaFileInfo("uri2", "Song2.mp3", 2000L, title = "Title2", artist = "Artist2")
        )
        val defaultIconUri = Uri.parse("content://default_icon")

        val result = buildSongListItems(songs, "test_key", defaultIconUri)

        assertEquals(4, result.size) // 2 headers + 2 songs

        // Play All
        assertEquals("action:play_all:test_key", result[0].mediaId)
        assertEquals("[Play All]", result[0].description.title)
        assertEquals(MediaItem.FLAG_PLAYABLE, result[0].flags)

        // Shuffle
        assertEquals("action:shuffle:test_key", result[1].mediaId)
        assertEquals("[Shuffle]", result[1].description.title)
        assertEquals(MediaItem.FLAG_PLAYABLE, result[1].flags)

        // Songs
        assertEquals("uri1", result[2].mediaId)
        assertEquals("Title1", result[2].description.title)
        assertEquals("Artist1", result[2].description.subtitle)
        assertEquals(defaultIconUri, result[2].description.iconUri)
        assertEquals(MediaItem.FLAG_PLAYABLE, result[2].flags)

        assertEquals("uri2", result[3].mediaId)
        assertEquals("Title2", result[3].description.title)
        assertEquals("Artist2", result[3].description.subtitle)
        assertEquals(defaultIconUri, result[3].description.iconUri)
        assertEquals(MediaItem.FLAG_PLAYABLE, result[3].flags)
    }


    @Test
    fun buildCategoryListItems_mapsCategoriesToMediaItems() {
        val categories = listOf("Rock", "Pop")
        val prefix = "category_prefix/"
        val iconUri = Uri.parse("content://category_icon")

        val result = buildCategoryListItems(categories, prefix, iconUri = iconUri)

        assertEquals(2, result.size)

        assertEquals("category_prefix/Rock", result[0].mediaId)
        assertEquals("Rock", result[0].description.title)
        assertEquals(iconUri, result[0].description.iconUri)
        assertEquals(MediaItem.FLAG_BROWSABLE, result[0].flags)

        assertEquals("category_prefix/Pop", result[1].mediaId)
        assertEquals("Pop", result[1].description.title)
        assertEquals(iconUri, result[1].description.iconUri)
        assertEquals(MediaItem.FLAG_BROWSABLE, result[1].flags)
    }

    @Test
    fun buildCategoryListItems_withCounts_includesCountsInTitle() {
        val categories = listOf("Rock", "Pop")
        val prefix = "category_prefix/"
        val counts = mapOf("Rock" to 10, "Pop" to 5)

        val result = buildCategoryListItems(categories, prefix, counts = counts)

        assertEquals(2, result.size)

        assertEquals("category_prefix/Rock", result[0].mediaId)
        assertEquals("Rock (10)", result[0].description.title)
        assertNull(result[0].description.iconUri)
        assertEquals(MediaItem.FLAG_BROWSABLE, result[0].flags)

        assertEquals("category_prefix/Pop", result[1].mediaId)
        assertEquals("Pop (5)", result[1].description.title)
        assertNull(result[1].description.iconUri)
        assertEquals(MediaItem.FLAG_BROWSABLE, result[1].flags)
    }

    @Test
    fun buildCategoryListItems_urlEncodesCategory() {
        val categories = listOf("Rock & Roll", "Pop/Dance")
        val prefix = "category_prefix/"

        val result = buildCategoryListItems(categories, prefix)

        assertEquals(2, result.size)

        assertEquals("category_prefix/Rock%20%26%20Roll", result[0].mediaId)
        assertEquals("Rock & Roll", result[0].description.title)

        assertEquals("category_prefix/Pop%2FDance", result[1].mediaId)
        assertEquals("Pop/Dance", result[1].description.title)
    }

}
