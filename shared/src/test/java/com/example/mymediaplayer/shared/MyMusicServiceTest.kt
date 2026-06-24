package com.example.mymediaplayer.shared

import android.graphics.BitmapFactory
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class MyMusicServiceTest {

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
    }

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
    fun calculateAlbumArtInSampleSize_downsamplesLargeArtwork() {
        val service = MyMusicService()
        val options = BitmapFactory.Options().apply {
            outWidth = 4096
            outHeight = 4096
        }

        val sampleSize = service.calculateAlbumArtInSampleSize(options)

        assertEquals(8, sampleSize)
    }

    @Test
    fun calculateAlbumArtInSampleSize_keepsSmallArtworkAtFullSize() {
        val service = MyMusicService()
        val options = BitmapFactory.Options().apply {
            outWidth = 320
            outHeight = 320
        }

        val sampleSize = service.calculateAlbumArtInSampleSize(options)

        assertEquals(1, sampleSize)
    }

    @Test
    fun decodeSampledBitmapFromStream_decodesAndDownsamplesImage() {
        val service = MyMusicService()
        val source = android.graphics.Bitmap.createBitmap(2048, 2048, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            source.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

        val bitmap = service.decodeSampledBitmapFromStream { java.io.ByteArrayInputStream(bytes) }

        assertNotNull(bitmap)
        assertEquals(512, bitmap!!.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun decodeSampledBitmapFromStream_returnsNullWhenStreamUnavailable() {
        val service = MyMusicService()

        val bitmap = service.decodeSampledBitmapFromStream { null }

        assertNull(bitmap)
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
    fun parentRequiresLoadedCache_falseForRootSearchHome_trueForDataIds() {
        val service = MyMusicService()

        // Cache-independent: must respond immediately even while scanning.
        assertFalse(service.parentRequiresLoadedCache(MyMusicService.ROOT_ID))
        assertFalse(service.parentRequiresLoadedCache(MyMusicService.SEARCH_ID))
        assertFalse(service.parentRequiresLoadedCache(MyMusicService.HOME_ID))

        // Cache-dependent top-level tabs.
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.SONGS_ID))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.SONGS_ALL_ID))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.PLAYLISTS_ID))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.ALBUMS_ID))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.GENRES_ID))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.ARTISTS_ID))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.DECADES_ID))

        // Cache-dependent prefix-based children (conservative default).
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.ALBUM_PREFIX + "Rock"))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.ARTIST_PREFIX + "Beatles"))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.GENRE_PREFIX + "Jazz"))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.PLAYLIST_PREFIX + "abc"))
        assertTrue(service.parentRequiresLoadedCache(MyMusicService.SMART_PLAYLIST_PREFIX + "flagged"))

        // Unknown IDs default to true (safer to defer than to over-respond).
        assertTrue(service.parentRequiresLoadedCache("something_unexpected"))
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
    fun loadCachedTreeIfAvailable_indexesStayLazyUntilFirstBrowse() {
        // Regression guard for issue #370. This test passes both before and after
        // removing the eager buildAlbumArtistIndexesFromCache() calls in
        // loadCachedTreeIfAvailable() — it is not a red/green TDD test, since that
        // removal is non-behavioral (every read path already calls
        // ensureMetadataIndexes() lazily, per benchmarkBuildHomeItemsWithUnindexedCache).
        // Its job is to lock in the lazy-build contract this PR relies on.
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        val cache = service.mediaCacheService

        cache.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song 1.mp3",
                sizeBytes = 100L,
                title = "Song 1",
                artist = "Artist",
                album = "Album",
                genre = "Rock",
                year = 1999
            )
        )

        assertFalse(cache.hasAlbumArtistIndexes())

        val items = service.buildHomeItems()

        assertTrue(cache.hasAlbumArtistIndexes())
        assertTrue(items.isNotEmpty())
    }

    @Test
    fun onTrimMemory_atRunningCriticalClearsIndexesButKeepsCachedFiles() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        val cache = service.mediaCacheService

        cache.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song 1.mp3",
                sizeBytes = 100L,
                title = "Song 1",
                artist = "Artist",
                album = "Album",
                genre = "Rock",
                year = 1999
            )
        )
        service.buildHomeItems()
        assertTrue(cache.hasAlbumArtistIndexes())

        service.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        assertFalse(cache.hasAlbumArtistIndexes())
        assertTrue(cache.hasCachedFiles())
    }

    @Test
    fun onTrimMemory_belowRunningCriticalLeavesIndexesIntact() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        val cache = service.mediaCacheService

        cache.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song 1.mp3",
                sizeBytes = 100L,
                title = "Song 1",
                artist = "Artist",
                album = "Album",
                genre = "Rock",
                year = 1999
            )
        )
        service.buildHomeItems()
        assertTrue(cache.hasAlbumArtistIndexes())

        service.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        assertTrue(cache.hasAlbumArtistIndexes())
    }

    @Test
    fun buildMediaItems_searchRootShowsPreviousSearches() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        service.getSharedPreferences("mymediaplayer_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("search_history", "hello\nworld")
            .commit()

        val items = service.buildMediaItems(MyMusicService.SEARCH_ID)

        assertEquals(2, items.size)
        assertEquals("hello", items[0].description.title.toString())
        assertEquals("world", items[1].description.title.toString())
        assertTrue(items[0].description.mediaId.orEmpty().startsWith("search_query:"))
    }

    @Test
    fun buildMediaItems_previousSearchShowsMatchingSongs() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello song"
            )
        )
        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song Two",
                sizeBytes = 1L,
                title = "other song"
            )
        )

        val items = service.buildMediaItems("search_query:hello")

        assertEquals("[Play All]", items[0].description.title.toString())
        assertEquals("[Shuffle]", items[1].description.title.toString())
        assertEquals("hello song", items[2].description.title.toString())
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
    fun playlistShortId_isShortAndDistinct() {
        val uri1 = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fplaylist.m3u"
        val uri2 = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fother.m3u"
        val id1 = playlistShortId(uri1)
        val id2 = playlistShortId(uri2)
        assertTrue("Short ID '$id1' should be under 10 chars", id1.length < 10)
        assertTrue("Short ID '$id2' should be under 10 chars", id2.length < 10)
        // Determinism: same URI always produces same short ID
        assertEquals("Short ID must be deterministic", id1, playlistShortId(uri1))
        // Distinctness: different URIs must not collide
        assertNotEquals("Different URIs should produce different short IDs", id1, id2)
    }

    @Test
    fun buildMediaItems_playlistsRoot_hasNoPlayAllOrShuffleEntries() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        service.mediaCacheService.addPlaylist(
            PlaylistInfo(uriString = "content://playlists/mix1.m3u", displayName = "Mix 1")
        )
        service.mediaCacheService.addPlaylist(
            PlaylistInfo(uriString = "content://playlists/mix2.m3u", displayName = "Mix 2")
        )

        val items = service.buildMediaItems("playlists")

        val actionItems = items.filter { item ->
            val id = item.description.mediaId.orEmpty()
            id.startsWith("action:play_all:") || id.startsWith("action:shuffle:")
        }
        assertTrue(
            "Expected no [Play All] / [Shuffle] entries at playlists root, found titles: " +
                actionItems.map { it.description.title.toString() },
            actionItems.isEmpty()
        )
    }

    @Test
    fun buildMediaItems_playlistsRoot_hasNoPlayAllOrShuffleEntries_whenNoDiscoveredPlaylists() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()
        // Deliberately do not addPlaylist or addFile — only smart playlists should appear.

        val items = service.buildMediaItems("playlists")

        val actionItems = items.filter { item ->
            val id = item.description.mediaId.orEmpty()
            id.startsWith("action:play_all:") || id.startsWith("action:shuffle:")
        }
        assertTrue(
            "Expected no [Play All] / [Shuffle] entries at playlists root with no discovered playlists, " +
                "found titles: " + actionItems.map { it.description.title.toString() },
            actionItems.isEmpty()
        )
    }

    // Regression tests for issue #382 (OOM crash loop persisting after #370).

    @Test
    fun tryBeginScan_onlyAllowsOneCallerAtATime() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()

        assertTrue(service.tryBeginScan())
        assertFalse(service.tryBeginScan())

        service.endScan()

        assertTrue(service.tryBeginScan())
    }

    @Test
    fun resolvePendingQueueIfNeeded_noopWhenNothingPending() {
        val service = Robolectric.buildService(MyMusicService::class.java).get()

        assertFalse(service.hasPendingQueueRestore())
        service.resolvePendingQueueIfNeeded()

        assertTrue(service.currentPlaylistQueue().isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun resolvePendingQueueIfNeeded_resolvesRealDataOnceCacheIsLoaded() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        service.seedPendingQueueRestoreForTesting(
            listOf("content://test/song1", "content://test/song2"),
            index = 1
        )
        assertTrue(service.hasPendingQueueRestore())
        assertTrue(service.currentPlaylistQueue().isEmpty())

        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song1.mp3",
                sizeBytes = 1L,
                title = "Song One"
            )
        )
        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song2.mp3",
                sizeBytes = 1L,
                title = "Song Two"
            )
        )

        service.resolvePendingQueueIfNeeded()

        assertFalse(service.hasPendingQueueRestore())
        val queue = service.currentPlaylistQueue()
        assertEquals(2, queue.size)
        assertEquals("Song One", queue[0].title)
        assertEquals("Song Two", queue[1].title)
    }

    @Test
    @Config(sdk = [33])
    fun restorePlaybackSnapshot_defersQueueRestoreWhenCacheNotLoaded() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        MyMusicService.getPrefs(service)
            .edit()
            .putString(
                "resume_queue_uris",
                "content://test/song1\ncontent://test/song2"
            )
            .putInt("resume_queue_index", 1)
            .putString("resume_queue_title", "All Songs")
            .apply()
        // Cache is deliberately empty here — this is the cold-start case.

        service.restorePlaybackSnapshot()

        assertTrue(
            "Expected no placeholder queue to be built before the cache loads",
            service.currentPlaylistQueue().isEmpty()
        )
        assertTrue(service.hasPendingQueueRestore())

        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song1.mp3",
                sizeBytes = 1L,
                title = "Song One"
            )
        )
        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song2.mp3",
                sizeBytes = 1L,
                title = "Song Two"
            )
        )
        service.resolvePendingQueueIfNeeded()

        val queue = service.currentPlaylistQueue()
        assertEquals(2, queue.size)
        assertEquals("Song One", queue[0].title)
        assertEquals("Song Two", queue[1].title)
    }

    @Test
    @Config(sdk = [33])
    fun savePlaybackSnapshot_doesNotWipeSavedQueueWhileRestoreIsPending() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        MyMusicService.getPrefs(service)
            .edit()
            .putString(
                "resume_queue_uris",
                "content://test/song1\ncontent://test/song2"
            )
            .putInt("resume_queue_index", 1)
            .putString("resume_queue_title", "All Songs")
            .apply()
        // Cache is deliberately empty — restorePlaybackSnapshot() will defer.
        service.restorePlaybackSnapshot()
        assertTrue(service.hasPendingQueueRestore())

        // Something (e.g. onDestroy(), onPlay()'s resume path) triggers a
        // snapshot save before the real cache load finishes.
        service.savePlaybackSnapshot()

        val prefs = MyMusicService.getPrefs(service)
        assertEquals(
            "The original saved queue must survive a snapshot save taken " +
                "before resolvePendingQueueIfNeeded() runs",
            "content://test/song1\ncontent://test/song2",
            prefs.getString("resume_queue_uris", null)
        )
        assertEquals(1, prefs.getInt("resume_queue_index", -1))
        assertEquals("All Songs", prefs.getString("resume_queue_title", null))
    }

    @Test
    @Config(sdk = [33])
    fun restorePlaybackSnapshot_resolvesImmediatelyWhenCacheAlreadyLoaded() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        service.mediaCacheService.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song1.mp3",
                sizeBytes = 1L,
                title = "Song One"
            )
        )
        MyMusicService.getPrefs(service)
            .edit()
            .putString("resume_queue_uris", "content://test/song1")
            .putInt("resume_queue_index", 0)
            .apply()

        service.restorePlaybackSnapshot()

        assertFalse(service.hasPendingQueueRestore())
        val queue = service.currentPlaylistQueue()
        assertEquals(1, queue.size)
        assertEquals("Song One", queue[0].title)
    }
}
