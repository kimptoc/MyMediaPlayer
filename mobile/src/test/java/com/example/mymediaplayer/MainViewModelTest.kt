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
            PlaybackStateCompat.STATE_PLAYING,
            "content://test/song1",
            "Song One",
            "Artist One",
            10_000L,
            1_000L,
            1f,
            180_000L
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
            )
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
}
