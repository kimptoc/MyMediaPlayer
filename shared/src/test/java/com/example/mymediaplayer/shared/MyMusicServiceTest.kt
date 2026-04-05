package com.example.mymediaplayer.shared

import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
    fun buildCategoryListItems_appliesCountsAsSubtitle() {
        val categories = listOf("Rock", "Pop")
        val counts = mapOf("Rock" to 2)

        val items = buildCategoryListItems(categories, "genre:", counts)

        assertEquals(2, items.size)
        assertEquals("genre:Rock", items[0].description.mediaId)
        assertEquals("Rock", items[0].description.title)
        assertEquals("2 songs", items[0].description.subtitle)
        assertEquals("genre:Pop", items[1].description.mediaId)
        assertEquals("Pop", items[1].description.title)
        assertNull(items[1].description.subtitle)
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

    @Test
    fun smartPlaylistIdFromQuery_mapsGenericVoiceQueries() {
        val service = MyMusicService()

        assertEquals("favorites", service.smartPlaylistIdFromQuery("play favorites"))
        assertEquals("recently_added", service.smartPlaylistIdFromQuery("play recently added"))
        assertEquals("most_played", service.smartPlaylistIdFromQuery("shuffle most played"))
        assertEquals(
            "not_heard_recently",
            service.smartPlaylistIdFromQuery("play havent heard in a while")
        )
        assertNull(service.smartPlaylistIdFromQuery("play coldplay"))
    }

    @Test
    fun resolvePlaybackActions_includesSeekWhenPlayerAvailable() {
        val service = MyMusicService()

        val actions = service.resolvePlaybackActions(
            state = PlaybackStateCompat.STATE_PLAYING,
            queueSize = 3,
            queueIndex = 1,
            canSeek = true
        )

        assertTrue(actions and PlaybackStateCompat.ACTION_SEEK_TO != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L)
    }

    @Test
    fun resolvePlaybackActions_excludesSeekWhenNoPlayer() {
        val service = MyMusicService()

        val actions = service.resolvePlaybackActions(
            state = PlaybackStateCompat.STATE_STOPPED,
            queueSize = 0,
            queueIndex = -1,
            canSeek = false
        )

        assertTrue(actions and PlaybackStateCompat.ACTION_PLAY != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_SEEK_TO == 0L)
    }

    @Test
    fun playlistEntriesForBrowse_putsUserPlaylistsBeforeSmartPlaylists() {
        val service = MyMusicService()
        val discovered = listOf(
            PlaylistInfo(
                uriString = "content://playlists/user_one.m3u",
                displayName = "User One.m3u"
            ),
            PlaylistInfo(
                uriString = "content://playlists/user_two.m3u",
                displayName = "User Two.m3u"
            )
        )

        val entries = service.playlistEntriesForBrowse(discovered)

        assertEquals(7, entries.size)
        assertEquals("User One", entries[0].title)
        assertEquals("User Two", entries[1].title)
        assertTrue(entries[0].mediaId.startsWith("playlist:"))
        assertTrue(entries[1].mediaId.startsWith("playlist:"))
        assertEquals("Favorites", entries[2].title)
        assertEquals("Flagged", entries[3].title)
        assertTrue(entries[2].mediaId.startsWith("smart_playlist:"))
        assertTrue(entries[3].mediaId.startsWith("smart_playlist:"))
    }

    @Test
    fun buildHomeItems_showsPlaylistsTileWhenOnlySmartPlaylistsAvailable() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One.mp3",
                sizeBytes = 1L,
                title = "Song One"
            )
        )
        // No discovered playlists — only smart playlists should be available

        val items = service.buildHomeItems()

        val playlistsTile = items.find { it.description.mediaId == "playlists" }
        assertNotNull("Playlists tile should show when songs exist (smart playlists available)", playlistsTile)
        assertEquals("Smart Playlists", playlistsTile!!.description.subtitle)
    }

    @Test
    fun buildHomeItems_showsPlaylistsTileWithCountWhenUserPlaylistsExist() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One.mp3",
                sizeBytes = 1L,
                title = "Song One"
            )
        )
        service.mediaCacheService.addPlaylist(
            PlaylistInfo(uriString = "content://test/playlist.m3u", displayName = "My Playlist.m3u")
        )

        val items = service.buildHomeItems()

        val playlistsTile = items.find { it.description.mediaId == "playlists" }
        assertNotNull(playlistsTile)
        assertEquals("1 playlist", playlistsTile!!.description.subtitle)
    }

    @Test
    fun buildHomeItems_hidesAllCategoriesWhenCacheEmpty() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        // No files added

        val items = service.buildHomeItems()

        assertTrue("Home should be empty when no songs loaded", items.isEmpty())
    }

    @Test
    fun playlistEntriesForBrowse_includesSmartPlaylistsWhenNoUserPlaylists() {
        val service = MyMusicService()

        val entries = service.playlistEntriesForBrowse(emptyList())

        assertEquals(5, entries.size)
        assertEquals("Favorites", entries[0].title)
        assertEquals("Flagged", entries[1].title)
        assertEquals("Recently Added", entries[2].title)
        assertEquals("Most Played", entries[3].title)
        assertEquals("Haven't Heard In A While", entries[4].title)
        assertNotNull(entries.firstOrNull { it.mediaId.endsWith("favorites") })
    }

    @Test
    fun playlistShortId_isDeterministicAndShort() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fplaylist.m3u"
        val shortId = uri.hashCode().toUInt().toString(36)
        assertTrue("Short ID '$shortId' should be under 10 chars", shortId.length < 10)
        assertEquals(shortId, uri.hashCode().toUInt().toString(36))
    }
}
