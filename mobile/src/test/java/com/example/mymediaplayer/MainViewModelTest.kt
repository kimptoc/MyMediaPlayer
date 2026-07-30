package com.example.mymediaplayer

import android.app.Application
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    @Test
    fun resetAfterScan_resetsStateAndSearchResults() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        viewModel.updateSearchQuery("hello")

        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello song"
            )
        )
        val playlists = listOf(
            PlaylistInfo(
                uriString = "content://test/playlist.m3u",
                displayName = "playlist.m3u"
            )
        )

        val state = viewModel.resetAfterScan(
            files = files,
            playlists = playlists,
            maxFiles = 12,
            scanMessage = "done"
        )

        assertFalse(state.scan.isScanning)
        assertEquals(files, state.scan.scannedFiles)
        assertEquals(playlists, state.scan.discoveredPlaylists)
        assertEquals(12, state.scan.lastScanLimit)
        assertEquals("done", state.scan.scanMessage)
        assertEquals(LibraryState(), state.library)
        assertEquals(1, state.search.searchResults.size)
        assertTrue(state.search.searchResults.first().title?.contains("hello") == true)
    }

    @Test
    fun resetAfterScan_clearsStalePlaylistSongCounts() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        seedUiState(
            viewModel,
            MainUiState(
                playlist = PlaylistMgmtState(
                    playlistSongCounts = mapOf("content://test/playlist.m3u" to 5)
                )
            )
        )

        val state = viewModel.resetAfterScan(
            files = emptyList(),
            playlists = emptyList(),
            maxFiles = 12
        )

        assertTrue(state.playlist.playlistSongCounts.isEmpty())
    }

    @Test
    fun updateSearchQuery_preservesSpacesAndFindsExactPhrase() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello world"
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song Two",
                sizeBytes = 1L,
                title = "hello"
            )
        )
        val playlists = listOf(
            PlaylistInfo(
                uriString = "content://test/playlist.m3u",
                displayName = "playlist.m3u"
            )
        )
        seedScanState(viewModel, files, playlists)

        viewModel.updateSearchQuery("hello world")
        val state = viewModel.uiState.value

        assertEquals("hello world", state.search.searchQuery)
        assertEquals(1, state.search.searchResults.size)
        assertEquals("content://test/song1", state.search.searchResults.first().uriString)
    }

    @Test
    fun clearSearch_resetsQueryAndResults() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello world"
            )
        )
        val playlists = listOf(
            PlaylistInfo(
                uriString = "content://test/playlist.m3u",
                displayName = "playlist.m3u"
            )
        )
        seedScanState(viewModel, files, playlists)
        viewModel.updateSearchQuery("hello")

        viewModel.clearSearch()
        val state = viewModel.uiState.value

        assertEquals("", state.search.searchQuery)
        assertTrue(state.search.searchResults.isEmpty())
    }

    @Test
    fun clearSearch_savesPreviousSearchQuery() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello world"
            )
        )
        seedScanState(viewModel, files, emptyList())

        viewModel.updateSearchQuery("hello")
        viewModel.clearSearch()
        val state = viewModel.uiState.value

        assertEquals(listOf("hello"), state.search.previousSearchQueries)
    }

    @Test
    fun clearSearch_withBlankQuery_doesNotRecordPreviousQuery() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello world"
            )
        )
        seedScanState(viewModel, files, emptyList())

        viewModel.clearSearch()
        val state = viewModel.uiState.value

        assertEquals("", state.search.searchQuery)
        assertTrue(state.search.previousSearchQueries.isEmpty())
    }

    @Test
    fun updateSearchQuery_whenClearedManually_savesPreviousSearchQuery() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        viewModel.updateSearchQuery("hello")
        viewModel.updateSearchQuery("")
        val state = viewModel.uiState.value

        assertEquals(listOf("hello"), state.search.previousSearchQueries)
    }

    @Test
    fun init_loadsPreviousSearchQueriesFromPrefs() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        app.getSharedPreferences("mymediaplayer_prefs", Application.MODE_PRIVATE)
            .edit()
            .putString("search_history", "hello\nworld")
            .commit()

        val viewModel = MainViewModel(app)

        assertEquals(listOf("hello", "world"), viewModel.uiState.value.search.previousSearchQueries)
    }

    @Test
    fun renamePlaylist_withBlankName_setsValidationMessage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = emptyList<MediaFileInfo>()
        val playlists = listOf(
            PlaylistInfo(
                uriString = "content://test/playlist.m3u",
                displayName = "playlist.m3u"
            )
        )
        seedScanState(viewModel, files, playlists)

        viewModel.renamePlaylist(playlists.first(), "   ")
        val state = viewModel.uiState.value

        assertEquals("Enter a playlist name", state.playlist.playlistMessage)
    }

    @Test
    fun updatePlaybackState_recordsPlayCountAndLastPlayedAt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        viewModel.updatePlaybackState(
            state = PlaybackStateCompat.STATE_PLAYING,
            mediaId = "content://test/song1",
            trackName = "Song One",
            artistName = "Artist One",
            positionMs = 10_000L,
            positionUpdatedAtElapsedMs = 1_000L,
            playbackSpeed = 1f,
            durationMs = 180_000L
        )
        val state = viewModel.uiState.value

        assertEquals(1, state.playCounts["content://test/song1"])
        assertTrue((state.lastPlayedAt["content://test/song1"] ?: 0L) > 0L)
    }

    @Test
    fun selectPlaylist_notHeardRecently_prioritizesNeverPlayedThenOldestPlayed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/songA",
                displayName = "Song A",
                sizeBytes = 1L,
                title = "Song A"
            ),
            MediaFileInfo(
                uriString = "content://test/songB",
                displayName = "Song B",
                sizeBytes = 1L,
                title = "Song B"
            ),
            MediaFileInfo(
                uriString = "content://test/songC",
                displayName = "Song C",
                sizeBytes = 1L,
                title = "Song C"
            )
        )
        val state = MainUiState(
            scan = ScanState(scannedFiles = files, lastScanLimit = 10),
            lastPlayedAt = mapOf(
                "content://test/songB" to 10L,
                "content://test/songC" to 20L
            ),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, state)

        viewModel.selectPlaylist(
            PlaylistInfo(
                uriString = MainViewModel.SMART_NOT_HEARD_RECENTLY,
                displayName = "Haven't Heard In A While.m3u"
            )
        )
        val orderedUris = viewModel.uiState.value.playlist.playlistSongs.map { it.uriString }

        assertEquals(
            listOf("content://test/songA", "content://test/songB", "content://test/songC"),
            orderedUris
        )
    }

    @Test
    fun selectPlaylist_flagged_returnsFlaggedSongs() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/songA",
                displayName = "Song A",
                sizeBytes = 1L,
                title = "Song A"
            ),
            MediaFileInfo(
                uriString = "content://test/songB",
                displayName = "Song B",
                sizeBytes = 1L,
                title = "Song B"
            ),
            MediaFileInfo(
                uriString = "content://test/songC",
                displayName = "Song C",
                sizeBytes = 1L,
                title = "Song C"
            )
        )
        val state = MainUiState(
            scan = ScanState(scannedFiles = files, lastScanLimit = 10),
            flaggedUris = setOf("content://test/songB", "content://test/songC"),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, state)

        viewModel.selectPlaylist(
            PlaylistInfo(
                uriString = MainViewModel.SMART_FLAGGED,
                displayName = "Flagged.m3u"
            )
        )
        val orderedUris = viewModel.uiState.value.playlist.playlistSongs.map { it.uriString }

        assertEquals(
            listOf("content://test/songB", "content://test/songC"),
            orderedUris
        )
    }

    @Test
    fun ensurePlaylistSongCount_smartPlaylist_doesNotTouchCounts() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        viewModel.ensurePlaylistSongCount(
            PlaylistInfo(uriString = MainViewModel.SMART_FAVORITES, displayName = "Favorites.m3u")
        )

        assertTrue(viewModel.uiState.value.playlist.playlistSongCounts.isEmpty())
    }

    @Test
    fun ensurePlaylistSongCount_alreadyCached_leavesCountUnchanged() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val playlist = PlaylistInfo(
            uriString = "content://test/playlist.m3u",
            displayName = "playlist.m3u"
        )
        val state = MainUiState(
            playlist = PlaylistMgmtState(playlistSongCounts = mapOf(playlist.uriString to 7))
        )
        seedUiState(viewModel, state)

        viewModel.ensurePlaylistSongCount(playlist)

        assertEquals(7, viewModel.uiState.value.playlist.playlistSongCounts[playlist.uriString])
    }

    @Test
    fun updateSearchQuery_onPlaylistsTab_scopesResultsToSelectedPlaylist() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello in library"
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song Two",
                sizeBytes = 1L,
                title = "hello in playlist"
            ),
            MediaFileInfo(
                uriString = "content://test/song3",
                displayName = "Song Three",
                sizeBytes = 1L,
                title = "unrelated"
            )
        )
        val state = MainUiState(
            scan = ScanState(
                scannedFiles = files,
                discoveredPlaylists = listOf(
                    PlaylistInfo(
                        uriString = MainViewModel.SMART_FAVORITES,
                        displayName = "Favorites.m3u"
                    )
                ),
                lastScanLimit = 10
            ),
            library = LibraryState(selectedTab = LibraryTab.Playlists),
            playlist = PlaylistMgmtState(
                selectedPlaylist = PlaylistInfo(
                    uriString = MainViewModel.SMART_FAVORITES,
                    displayName = "Favorites.m3u"
                ),
                playlistSongs = listOf(
                    MediaFileInfo(
                        uriString = "content://test/song2",
                        displayName = "Song Two",
                        sizeBytes = 1L,
                        title = "hello in playlist"
                    )
                )
            ),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, state)

        viewModel.updateSearchQuery("hello")
        val results = viewModel.uiState.value.search.searchResults

        assertEquals(1, results.size)
        assertEquals("content://test/song2", results.first().uriString)
    }

    @Test
    fun updateSearchQuery_onOtherTabs_usesLibraryFiles() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello library"
            )
        )
        seedScanState(viewModel, files, emptyList())

        viewModel.updateSearchQuery("hello")
        val results = viewModel.uiState.value.search.searchResults

        assertEquals(1, results.size)
        assertEquals("content://test/song1", results.first().uriString)
    }

    @Test
    fun selectTab_toPlaylistsWithSelectedPlaylist_rerunsSearch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello in library"
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song Two",
                sizeBytes = 1L,
                title = "hello in playlist"
            )
        )
        val state = MainUiState(
            scan = ScanState(scannedFiles = files, lastScanLimit = 10),
            library = LibraryState(selectedTab = LibraryTab.Songs),
            playlist = PlaylistMgmtState(
                selectedPlaylist = PlaylistInfo(
                    uriString = MainViewModel.SMART_FAVORITES,
                    displayName = "Favorites.m3u"
                ),
                playlistSongs = listOf(
                    MediaFileInfo(
                        uriString = "content://test/song2",
                        displayName = "Song Two",
                        sizeBytes = 1L,
                        title = "hello in playlist"
                    )
                )
            ),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, state)
        viewModel.updateSearchQuery("hello")
        val initialResults = viewModel.uiState.value.search.searchResults
        assertEquals(2, initialResults.size)

        viewModel.selectTab(LibraryTab.Playlists)
        val scopedResults = viewModel.uiState.value.search.searchResults
        assertEquals(1, scopedResults.size)
        assertEquals("content://test/song2", scopedResults.first().uriString)
    }

    @Test
    fun clearSelectedPlaylist_rerunsSearchAgainstLibrary() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "hello library"
            )
        )
        val state = MainUiState(
            scan = ScanState(scannedFiles = files, lastScanLimit = 10),
            library = LibraryState(selectedTab = LibraryTab.Playlists),
            playlist = PlaylistMgmtState(
                selectedPlaylist = PlaylistInfo(
                    uriString = MainViewModel.SMART_FAVORITES,
                    displayName = "Favorites.m3u"
                ),
                playlistSongs = emptyList()
            ),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, state)
        viewModel.updateSearchQuery("hello")
        assertTrue(viewModel.uiState.value.search.searchResults.isEmpty())

        viewModel.clearSelectedPlaylist()
        val results = viewModel.uiState.value.search.searchResults
        assertEquals(1, results.size)
        assertEquals("content://test/song1", results.first().uriString)
    }

    private fun seedScanState(
        viewModel: MainViewModel,
        files: List<MediaFileInfo>,
        playlists: List<PlaylistInfo>
    ) {
        seedUiState(
            viewModel,
            MainUiState(
                scan = ScanState(
                    scannedFiles = files,
                    discoveredPlaylists = playlists,
                    lastScanLimit = 10
                )
            )
        )
    }

    @Test
    fun updateRepeatMode_updatesRepeatModeInUiState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        viewModel.updateRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE, viewModel.uiState.value.playback.repeatMode)
    }

    private fun seedUiState(viewModel: MainViewModel, state: MainUiState) {
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<MainUiState>
        flow.value = state
    }

    private fun clearPrefs(app: Application) {
        app.getSharedPreferences("mymediaplayer_prefs", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun updateQueueState_withValidTitleAndSize_updatesStateCorrectly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        viewModel.updateQueueState(
            queueTitle = "My Playlist",
            queueSize = 10,
            activeIndex = 4
        )

        val playbackState = viewModel.uiState.value.playback
        assertTrue(playbackState.isPlayingPlaylist)
        assertEquals("My Playlist", playbackState.queueTitle)
        assertEquals("5/10", playbackState.queuePosition)
        assertTrue(playbackState.hasPrev)
        assertTrue(playbackState.hasNext)
    }

    @Test
    fun updateQueueState_withNullTitle_resetsQueueFields() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        viewModel.updateQueueState(
            queueTitle = null,
            queueSize = 10,
            activeIndex = 4
        )

        val playbackState = viewModel.uiState.value.playback
        assertFalse(playbackState.isPlayingPlaylist)
        assertEquals(null, playbackState.queueTitle)
        assertEquals(null, playbackState.queuePosition)
        assertFalse(playbackState.hasPrev)
        assertFalse(playbackState.hasNext)
    }

    @Test
    fun updateQueueState_atBeginningOfQueue_hasNoPrev() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        viewModel.updateQueueState(
            queueTitle = "My Playlist",
            queueSize = 10,
            activeIndex = 0
        )

        val playbackState = viewModel.uiState.value.playback
        assertTrue(playbackState.isPlayingPlaylist)
        assertEquals("My Playlist", playbackState.queueTitle)
        assertEquals("1/10", playbackState.queuePosition)
        assertFalse(playbackState.hasPrev)
        assertTrue(playbackState.hasNext)
    }

    @Test
    fun updateQueueState_atEndOfQueue_hasNoNext() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        viewModel.updateQueueState(
            queueTitle = "My Playlist",
            queueSize = 10,
            activeIndex = 9
        )

        val playbackState = viewModel.uiState.value.playback
        assertTrue(playbackState.isPlayingPlaylist)
        assertEquals("My Playlist", playbackState.queueTitle)
        assertEquals("10/10", playbackState.queuePosition)
        assertTrue(playbackState.hasPrev)
        assertFalse(playbackState.hasNext)
    }

    @Test
    fun updateQueueState_withList_updatesStateCorrectly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val queueItems = listOf(
            QueueEntry(queueId = 1L, mediaId = "media1", title = "Song 1"),
            QueueEntry(queueId = 2L, mediaId = "media2", title = "Song 2"),
            QueueEntry(queueId = 3L, mediaId = "media3", title = "Song 3")
        )

        viewModel.updateQueueState(
            queueTitle = "My Playlist",
            queueItems = queueItems,
            activeQueueId = 2L
        )

        val playbackState = viewModel.uiState.value.playback
        assertTrue(playbackState.isPlayingPlaylist)
        assertEquals("My Playlist", playbackState.queueTitle)
        assertEquals("2/3", playbackState.queuePosition)
        assertEquals(queueItems, playbackState.queueItems)
        assertEquals(2L, playbackState.activeQueueId)
        assertTrue(playbackState.hasPrev)
        assertTrue(playbackState.hasNext)
    }

    @Test
    fun setAlbumSortMode_whenModeIsAlreadySame_returnsEarly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val initialAlbums = listOf("Album A", "Album B")
        seedUiState(
            viewModel,
            MainUiState(
                library = LibraryState(
                    albumSortMode = AlbumSortMode.Name,
                    albums = initialAlbums
                )
            )
        )

        viewModel.setAlbumSortMode(AlbumSortMode.Name)

        val state = viewModel.uiState.value
        assertEquals(AlbumSortMode.Name, state.library.albumSortMode)
        assertEquals(initialAlbums, state.library.albums)
    }

    @Test
    fun setAlbumSortMode_withoutIndexes_setsModeButDoesNotReSort() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        val initialAlbums = listOf("Album B", "Album A")
        seedUiState(
            viewModel,
            MainUiState(
                library = LibraryState(
                    albumSortMode = AlbumSortMode.Name,
                    albums = initialAlbums
                )
            )
        )

        viewModel.setAlbumSortMode(AlbumSortMode.DateAddedDesc)

        val state = viewModel.uiState.value
        assertEquals(AlbumSortMode.DateAddedDesc, state.library.albumSortMode)
        assertEquals(initialAlbums, state.library.albums)
    }

    @Test
    fun setAlbumSortMode_withIndexes_sortsByName() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        // Retrieve mediaCacheService using reflection
        val serviceField = viewModel.javaClass.getDeclaredField("mediaCacheService")
        serviceField.isAccessible = true
        val mediaCacheService = serviceField.get(viewModel) as com.example.mymediaplayer.shared.MediaCacheService

        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                album = "Z-Album",
                addedAtMs = 1000L
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song Two",
                sizeBytes = 1L,
                album = "A-Album",
                addedAtMs = 5000L
            )
        )

        mediaCacheService.addAllFiles(files)
        mediaCacheService.buildAlbumArtistIndexesFromCache()

        seedUiState(
            viewModel,
            MainUiState(
                library = LibraryState(
                    albumSortMode = AlbumSortMode.DateAddedDesc,
                    albums = listOf("Z-Album", "A-Album")
                )
            )
        )

        viewModel.setAlbumSortMode(AlbumSortMode.Name)

        val state = viewModel.uiState.value
        assertEquals(AlbumSortMode.Name, state.library.albumSortMode)
        assertEquals(listOf("A-Album", "Z-Album"), state.library.albums)
    }

    @Test
    fun setAlbumSortMode_withIndexes_sortsByDateAddedDesc() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        // Retrieve mediaCacheService using reflection
        val serviceField = viewModel.javaClass.getDeclaredField("mediaCacheService")
        serviceField.isAccessible = true
        val mediaCacheService = serviceField.get(viewModel) as com.example.mymediaplayer.shared.MediaCacheService

        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                album = "Old-Album",
                addedAtMs = 1000L
            ),
            MediaFileInfo(
                uriString = "content://test/song2",
                displayName = "Song Two",
                sizeBytes = 1L,
                album = "New-Album",
                addedAtMs = 5000L
            )
        )

        mediaCacheService.addAllFiles(files)
        mediaCacheService.buildAlbumArtistIndexesFromCache()

        seedUiState(
            viewModel,
            MainUiState(
                library = LibraryState(
                    albumSortMode = AlbumSortMode.Name,
                    albums = listOf("New-Album", "Old-Album")
                )
            )
        )

        viewModel.setAlbumSortMode(AlbumSortMode.DateAddedDesc)

        val state = viewModel.uiState.value
        assertEquals(AlbumSortMode.DateAddedDesc, state.library.albumSortMode)
        assertEquals(listOf("New-Album", "Old-Album"), state.library.albums)
    }

    @Test
    fun selectArtist_updatesUiStateAndFiltersSongsCorrectly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val mediaCacheField = viewModel.javaClass.getDeclaredField("mediaCacheService")
        mediaCacheField.isAccessible = true
        val mediaCache = mediaCacheField.get(viewModel) as com.example.mymediaplayer.shared.MediaCacheService

        val song1 = MediaFileInfo(
            uriString = "content://test/song1",
            displayName = "Song One",
            sizeBytes = 1L,
            title = "Song One",
            artist = "Artist A",
            album = "Album X",
            genre = "Rock"
        )
        val song2 = MediaFileInfo(
            uriString = "content://test/song2",
            displayName = "Song Two",
            sizeBytes = 1L,
            title = "Song Two",
            artist = "Artist B",
            album = "Album Y",
            genre = "Pop"
        )
        val song3 = MediaFileInfo(
            uriString = "content://test/song3",
            displayName = "Song Three",
            sizeBytes = 1L,
            title = "Song Three",
            artist = "Artist A",
            album = "Album Z",
            genre = "Jazz"
        )

        mediaCache.addAllFiles(listOf(song1, song2, song3))
        mediaCache.buildAlbumArtistIndexesFromCache()

        // Seed some pre-existing selections to ensure they get cleared
        seedUiState(
            viewModel,
            viewModel.uiState.value.copy(
                library = viewModel.uiState.value.library.copy(
                    selectedAlbum = "Album X",
                    selectedGenre = "Rock",
                    selectedArtist = "Old Artist"
                )
            )
        )

        viewModel.selectArtist("Artist A")

        val state = viewModel.uiState.value
        assertEquals("Artist A", state.library.selectedArtist)
        assertEquals(null, state.library.selectedAlbum)
        assertEquals(null, state.library.selectedGenre)
        assertEquals(2, state.library.filteredSongs.size)
        assertTrue(state.library.filteredSongs.contains(song1))
        assertTrue(state.library.filteredSongs.contains(song3))
        assertFalse(state.library.filteredSongs.contains(song2))
    }

    @Test
    fun setTreeUri_updatesTreeUriAndResetsMetadataKey() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val treeUriField = MainViewModel::class.java.getDeclaredField("treeUri")
        treeUriField.isAccessible = true

        val metadataKeyField = MainViewModel::class.java.getDeclaredField("metadataKey")
        metadataKeyField.isAccessible = true
        metadataKeyField.set(viewModel, "some_metadata_key")

        val targetUri = android.net.Uri.parse("content://my/tree/uri")
        viewModel.setTreeUri(targetUri)

        assertEquals(targetUri, treeUriField.get(viewModel))
        org.junit.Assert.assertNull(metadataKeyField.get(viewModel))
    }

    @Test
    fun clearCategorySelection_clearsCategorySelectionState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val initialLibraryState = LibraryState(
            selectedAlbum = "Album A",
            selectedGenre = "Genre A",
            selectedArtist = "Artist A",
            filteredSongs = listOf(
                MediaFileInfo(
                    uriString = "content://test/songA",
                    displayName = "Song A",
                    sizeBytes = 100L
                )
            )
        )
        seedUiState(
            viewModel,
            MainUiState(
                library = initialLibraryState,
                isPreferencesLoading = false
            )
        )

        viewModel.clearCategorySelection()

        val libraryState = viewModel.uiState.value.library
        assertEquals(null, libraryState.selectedAlbum)
        assertEquals(null, libraryState.selectedGenre)
        assertEquals(null, libraryState.selectedArtist)
        assertTrue(libraryState.filteredSongs.isEmpty())
    }
}
