package com.example.mymediaplayer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import com.example.mymediaplayer.shared.PlaylistService
import java.lang.reflect.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class MockSongPlaylistsPlaylistService : PlaylistService() {
    val readPlaylists: MutableMap<String, List<MediaFileInfo>> = mutableMapOf()
    val writePlaylists: MutableMap<String, List<MediaFileInfo>> = mutableMapOf()
    var failOverwrite: Boolean = false
    var failRead: Boolean = false
    var overwriteCallCount: Int = 0

    override fun readPlaylist(context: Context, playlistUri: Uri): List<MediaFileInfo> {
        if (failRead) return emptyList()
        return readPlaylists[playlistUri.toString()] ?: emptyList()
    }

    override fun overwritePlaylist(
        context: Context,
        playlistUri: Uri,
        files: List<MediaFileInfo>
    ): Boolean {
        overwriteCallCount += 1
        if (failOverwrite) return false
        writePlaylists[playlistUri.toString()] = files
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
class MainViewModelSongPlaylistsTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel
    private lateinit var mockService: MockSongPlaylistsPlaylistService

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(app)
        mockService = MockSongPlaylistsPlaylistService()

        val field: Field = MainViewModel::class.java.getDeclaredField("playlistService")
        field.isAccessible = true
        field.set(viewModel, mockService)

        app.getSharedPreferences("mymediaplayer_prefs", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun openSongPlaylistsDialog_setsSongAndLoadingTrue() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")

        viewModel.openSongPlaylistsDialog(song)

        val state = viewModel.uiState.value
        assertEquals(song, state.playlist.songPlaylistsDialogSong)
        assertTrue(state.playlist.songPlaylistsLoading)
    }

    @Test
    fun confirmSongPlaylistsDialogChanges_withoutSong_doesNothing() {
        val initial = viewModel.uiState.value
        viewModel.confirmSongPlaylistsDialogChanges()
        assertEquals(initial, viewModel.uiState.value)
    }

    @Test
    fun cancelSongPlaylistsDialog_clearsAllDialogState() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        viewModel.openSongPlaylistsDialog(song)

        viewModel.cancelSongPlaylistsDialog()

        val state = viewModel.uiState.value
        assertNull(state.playlist.songPlaylistsDialogSong)
        assertTrue(state.playlist.songPlaylistsContainingUris.isEmpty())
        assertTrue(state.playlist.songPlaylistsToRemove.isEmpty())
        assertFalse(state.playlist.songPlaylistsLoading)
    }

    @Test
    fun removeSongFromPlaylists_emptyPlaylists_isNoop() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")

        viewModel.removeSongFromPlaylists(song, emptyList())

        assertEquals(0, mockService.overwriteCallCount)
    }

    @Test
    fun removeSongFromPlaylists_filtersSongFromPlaylist() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        val otherSong = MediaFileInfo(uriString = "content://song2", displayName = "song2.mp3", sizeBytes = 0L, title = "Song 2")
        val playlistUri = "content://test/playlist1.m3u"
        val playlist = PlaylistInfo(uriString = playlistUri, displayName = "playlist1.m3u")
        mockService.readPlaylists[playlistUri] = listOf(song, otherSong)

        viewModel.removeSongFromPlaylists(song, listOf(playlist))

        // Synchronous: the launch is on IO but the mock is in-memory
        val written = mockService.writePlaylists[playlistUri]
        assertNotNull(written)
        assertEquals(listOf(otherSong), written)
    }

    @Test
    fun removeSongFromPlaylists_songNotInPlaylist_doesNotWrite() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        val otherSong = MediaFileInfo(uriString = "content://song2", displayName = "song2.mp3", sizeBytes = 0L, title = "Song 2")
        val playlistUri = "content://test/playlist1.m3u"
        val playlist = PlaylistInfo(uriString = playlistUri, displayName = "playlist1.m3u")
        mockService.readPlaylists[playlistUri] = listOf(otherSong)

        viewModel.removeSongFromPlaylists(song, listOf(playlist))

        assertEquals(0, mockService.overwriteCallCount)
    }

    @Test
    fun removeSongFromPlaylists_skipsSmartPlaylists() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        val smartPlaylist = PlaylistInfo(
            uriString = MainViewModel.SMART_FAVORITES,
            displayName = "Favorites.m3u"
        )

        viewModel.removeSongFromPlaylists(song, listOf(smartPlaylist))

        assertEquals(0, mockService.overwriteCallCount)
    }

    @Test
    fun removeSongFromPlaylists_failure_recordsFailureMessage() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        val otherSong = MediaFileInfo(uriString = "content://song2", displayName = "song2.mp3", sizeBytes = 0L, title = "Song 2")
        val playlistUri = "content://test/playlist1.m3u"
        val playlist = PlaylistInfo(uriString = playlistUri, displayName = "playlist1.m3u")
        mockService.readPlaylists[playlistUri] = listOf(song, otherSong)
        mockService.failOverwrite = true

        viewModel.removeSongFromPlaylists(song, listOf(playlist))

        assertTrue(mockService.overwriteCallCount >= 1)
        val state = viewModel.uiState.value
        assertEquals("Failed to update 1 playlist(s)", state.playlist.playlistMessage)
    }

    @Test
    fun removeSongFromPlaylists_success_setsRemovedMessage() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        val otherSong = MediaFileInfo(uriString = "content://song2", displayName = "song2.mp3", sizeBytes = 0L, title = "Song 2")
        val playlistUri = "content://test/playlist1.m3u"
        val playlist = PlaylistInfo(uriString = playlistUri, displayName = "playlist1.m3u")
        mockService.readPlaylists[playlistUri] = listOf(song, otherSong)

        viewModel.removeSongFromPlaylists(song, listOf(playlist))

        val state = viewModel.uiState.value
        assertEquals("Removed from 1 playlist(s)", state.playlist.playlistMessage)
    }

    @Test
    fun removeSongFromPlaylists_emptyAfterRemoval_setsSkippedMessage() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        val playlistUri = "content://test/playlist1.m3u"
        val playlist = PlaylistInfo(uriString = playlistUri, displayName = "playlist1.m3u")
        mockService.readPlaylists[playlistUri] = listOf(song)

        viewModel.removeSongFromPlaylists(song, listOf(playlist))

        val state = viewModel.uiState.value
        assertEquals(
            "Removed from 0; left 1 playlist(s) empty",
            state.playlist.playlistMessage
        )
    }

    @Test
    fun toggleSongPlaylistRemoval_uncheckingContainingPlaylist_addsToRemoveSet() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        val playlist = PlaylistInfo(uriString = "content://test/playlist1.m3u", displayName = "playlist1.m3u")
        viewModel.openSongPlaylistsDialog(song)
        // Pretend the playlist contains the song
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<MainUiState>
        val current = flow.value
        flow.value = current.copy(
            playlist = current.playlist.copy(
                songPlaylistsDialogSong = song,
                songPlaylistsContainingUris = setOf(playlist.uriString),
                songPlaylistsLoading = false
            )
        )

        viewModel.toggleSongPlaylistRemoval(playlist, false)

        val state = viewModel.uiState.value
        assertTrue(playlist.uriString in state.playlist.songPlaylistsToRemove)
    }

    @Test
    fun toggleSongPlaylistRemoval_uncheckingNonContainingPlaylist_isNoop() {
        val song = MediaFileInfo(uriString = "content://song1", displayName = "song1.mp3", sizeBytes = 0L, title = "Song 1")
        val playlist = PlaylistInfo(uriString = "content://test/playlist1.m3u", displayName = "playlist1.m3u")
        viewModel.openSongPlaylistsDialog(song)

        viewModel.toggleSongPlaylistRemoval(playlist, false)

        val state = viewModel.uiState.value
        assertTrue(state.playlist.songPlaylistsToRemove.isEmpty())
    }
}
