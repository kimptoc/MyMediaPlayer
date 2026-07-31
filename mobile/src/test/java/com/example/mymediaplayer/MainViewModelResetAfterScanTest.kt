package com.example.mymediaplayer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelResetAfterScanTest {

    @Test
    fun testResetAfterScan_basicAndDefaultParameters() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val files = listOf(
            MediaFileInfo(uriString = "content://song1", displayName = "Song One", sizeBytes = 100L)
        )
        val playlists = listOf(
            PlaylistInfo(uriString = "content://playlist1", displayName = "Playlist One")
        )

        val state = viewModel.resetAfterScan(
            files = files,
            playlists = playlists,
            maxFiles = 42
        )

        assertFalse(state.scan.isScanning)
        assertEquals(files, state.scan.scannedFiles)
        assertEquals(playlists, state.scan.discoveredPlaylists)
        assertEquals(42, state.scan.lastScanLimit)
        assertFalse(state.scan.deepScanEnabled)
        assertNull(state.scan.scanMessage)
        assertNull(state.scan.scanProgress)
    }

    @Test
    fun testResetAfterScan_playlistSortingIsCaseInsensitive() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val playlists = listOf(
            PlaylistInfo(uriString = "content://p3", displayName = "b_playlist"),
            PlaylistInfo(uriString = "content://p1", displayName = "A_playlist"),
            PlaylistInfo(uriString = "content://p2", displayName = "C_playlist")
        )

        val state = viewModel.resetAfterScan(
            files = emptyList(),
            playlists = playlists,
            maxFiles = 10
        )

        val sorted = state.scan.discoveredPlaylists
        assertEquals(3, sorted.size)
        assertEquals("A_playlist", sorted[0].displayName)
        assertEquals("b_playlist", sorted[1].displayName)
        assertEquals("C_playlist", sorted[2].displayName)
    }

    @Test
    fun testResetAfterScan_libraryStateIsResetAndPlaylistStateIsCleared() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val initialUiState = MainUiState(
            library = LibraryState(selectedTab = LibraryTab.Playlists),
            playlist = PlaylistMgmtState(
                selectedPlaylist = PlaylistInfo(uriString = "content://playlist1", displayName = "Playlist 1"),
                playlistSongs = listOf(MediaFileInfo(uriString = "content://song1", displayName = "Song 1", sizeBytes = 100L)),
                isPlaylistLoading = true,
                manualPlaylistSongs = listOf(MediaFileInfo(uriString = "content://song1", displayName = "Song 1", sizeBytes = 100L)),
                playlistSongCounts = mapOf("content://playlist1" to 1)
            )
        )
        seedUiState(viewModel, initialUiState)

        val state = viewModel.resetAfterScan(
            files = emptyList(),
            playlists = emptyList(),
            maxFiles = 10
        )

        assertEquals(LibraryState(), state.library)
        assertNull(state.playlist.selectedPlaylist)
        assertTrue(state.playlist.playlistSongs.isEmpty())
        assertFalse(state.playlist.isPlaylistLoading)
        assertTrue(state.playlist.manualPlaylistSongs.isEmpty())
        assertTrue(state.playlist.playlistSongCounts.isEmpty())
    }

    @Test
    fun testResetAfterScan_preservesOtherStateProperties() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val initialUiState = MainUiState(
            favoriteUris = setOf("content://fav1"),
            flaggedUris = setOf("content://flag1"),
            playCounts = mapOf("content://song1" to 5),
            lastPlayedAt = mapOf("content://song1" to 123456L),
            isPreferencesLoading = false,
            playback = PlaybackState(queueTitle = "Current Queue")
        )
        seedUiState(viewModel, initialUiState)

        val state = viewModel.resetAfterScan(
            files = emptyList(),
            playlists = emptyList(),
            maxFiles = 10
        )

        assertEquals(setOf("content://fav1"), state.favoriteUris)
        assertEquals(setOf("content://flag1"), state.flaggedUris)
        assertEquals(mapOf("content://song1" to 5), state.playCounts)
        assertEquals(mapOf("content://song1" to 123456L), state.lastPlayedAt)
        assertFalse(state.isPreferencesLoading)
        assertEquals("Current Queue", state.playback.queueTitle)
    }

    @Test
    fun testResetAfterScan_appliesSearchQueryCorrectly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)

        val initialUiState = MainUiState(
            search = SearchState(searchQuery = "rock")
        )
        seedUiState(viewModel, initialUiState)

        val files = listOf(
            MediaFileInfo(uriString = "content://song1", displayName = "Song 1", sizeBytes = 10L, title = "Classical Theme"),
            MediaFileInfo(uriString = "content://song2", displayName = "Song 2", sizeBytes = 10L, title = "Hard Rock Song"),
            MediaFileInfo(uriString = "content://song3", displayName = "Song 3", sizeBytes = 10L, title = "Rock Ballad")
        )

        val state = viewModel.resetAfterScan(
            files = files,
            playlists = emptyList(),
            maxFiles = 100
        )

        val results = state.search.searchResults
        assertEquals(2, results.size)
        assertTrue(results.any { it.uriString == "content://song2" })
        assertTrue(results.any { it.uriString == "content://song3" })
    }
}
