package com.example.mymediaplayer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import com.example.mymediaplayer.shared.PlaylistService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.ByteArrayOutputStream
import java.lang.reflect.Field

class MockPlaylistService : PlaylistService() {
    var writePlaylistCalled = false
    var passedName = ""
    var passedFiles = emptyList<MediaFileInfo>()
    var mockResult: PlaylistInfo? = null

    override fun writePlaylistWithName(
        context: Context,
        treeUri: Uri,
        files: List<MediaFileInfo>,
        name: String
    ): PlaylistInfo? {
        writePlaylistCalled = true
        passedName = name
        passedFiles = files
        return mockResult
    }
}

@RunWith(RobolectricTestRunner::class)
class MainViewModelPlaylistCreationTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel
    private lateinit var mockPlaylistService: MockPlaylistService

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(app)
        mockPlaylistService = MockPlaylistService()

        // Inject mock PlaylistService via reflection
        val field: Field = MainViewModel::class.java.getDeclaredField("playlistService")
        field.isAccessible = true
        field.set(viewModel, mockPlaylistService)

        // Clear prefs
        app.getSharedPreferences("mymediaplayer_prefs", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun createPlaylistFromSongs_noFolderSelected_returnsNullAndShowsMessage() {
        val files = listOf(MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1"))
        val result = viewModel.createPlaylistFromSongs("My Playlist", files)

        assertNull(result)
        val state = viewModel.uiState.value
        assertEquals("Select a folder first.", state.playlist.playlistMessage)
        assertTrue(!mockPlaylistService.writePlaylistCalled)
    }

    @Test
    fun createPlaylistFromSongs_noSongsAdded_returnsNullAndShowsMessage() {
        viewModel.setTreeUri(Uri.parse("content://tree"))
        val files = emptyList<MediaFileInfo>()
        val result = viewModel.createPlaylistFromSongs("My Playlist", files)

        assertNull(result)
        val state = viewModel.uiState.value
        assertEquals("Add songs first.", state.playlist.playlistMessage)
        assertTrue(!mockPlaylistService.writePlaylistCalled)
    }

    @Test
    fun createPlaylistFromSongs_success_createsPlaylistAndUpdatesState() {
        viewModel.setTreeUri(Uri.parse("content://tree"))
        val files = listOf(MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1"))

        val expectedResult = PlaylistInfo("content://playlist.m3u", "My Playlist.m3u")
        mockPlaylistService.mockResult = expectedResult

        val result = viewModel.createPlaylistFromSongs("My Playlist", files)

        assertEquals(expectedResult, result)
        assertTrue(mockPlaylistService.writePlaylistCalled)
        assertEquals("My Playlist", mockPlaylistService.passedName)
        assertEquals(files, mockPlaylistService.passedFiles)

        val state = viewModel.uiState.value
        assertEquals("Created My Playlist.m3u", state.playlist.playlistMessage)
        assertTrue(state.scan.discoveredPlaylists.contains(expectedResult))
    }

    @Test
    fun createPlaylistFromSongs_emptyName_usesTimestampName() {
        viewModel.setTreeUri(Uri.parse("content://tree"))
        val files = listOf(MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1"))

        val expectedResult = PlaylistInfo("content://playlist_123.m3u", "playlist_123.m3u")
        mockPlaylistService.mockResult = expectedResult

        val result = viewModel.createPlaylistFromSongs("   ", files)

        assertEquals(expectedResult, result)
        assertTrue(mockPlaylistService.writePlaylistCalled)
        assertTrue(mockPlaylistService.passedName.startsWith("playlist_"))
    }

    @Test
    fun createPlaylistFromSongs_failure_returnsNullAndShowsMessage() {
        viewModel.setTreeUri(Uri.parse("content://tree"))
        val files = listOf(MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1"))

        mockPlaylistService.mockResult = null

        val result = viewModel.createPlaylistFromSongs("My Playlist", files)

        assertNull(result)
        assertTrue(mockPlaylistService.writePlaylistCalled)

        val state = viewModel.uiState.value
        assertEquals("Failed to create playlist", state.playlist.playlistMessage)
    }

    @Test
    fun createManualPlaylist_noFolderSelected_returnsAndShowsMessage() {
        val file = MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1")
        viewModel.addToManualPlaylist(file)

        viewModel.createManualPlaylist("Manual Playlist")

        val state = viewModel.uiState.value
        assertEquals("Select a folder first.", state.playlist.playlistMessage)
        assertTrue(!mockPlaylistService.writePlaylistCalled)
    }

    @Test
    fun createManualPlaylist_noSongsAdded_returnsAndShowsMessage() {
        viewModel.setTreeUri(Uri.parse("content://tree"))

        viewModel.createManualPlaylist("Manual Playlist")

        val state = viewModel.uiState.value
        assertEquals("Add songs first.", state.playlist.playlistMessage)
        assertTrue(!mockPlaylistService.writePlaylistCalled)
    }

    @Test
    fun createManualPlaylist_failure_showsFailureMessage() {
        viewModel.setTreeUri(Uri.parse("content://tree"))
        val file = MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1")
        viewModel.addToManualPlaylist(file)

        mockPlaylistService.mockResult = null

        viewModel.createManualPlaylist("Manual Playlist")

        val state = viewModel.uiState.value
        assertEquals("Failed to create playlist", state.playlist.playlistMessage)
        assertTrue(mockPlaylistService.writePlaylistCalled)
    }

    @Test
    fun createManualPlaylist_success_updatesStateAndDiscoveredPlaylists() {
        viewModel.setTreeUri(Uri.parse("content://tree"))
        val file = MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1")
        viewModel.addToManualPlaylist(file)

        val expectedResult = PlaylistInfo("content://playlist.m3u", "Manual Playlist.m3u")
        mockPlaylistService.mockResult = expectedResult

        viewModel.createManualPlaylist("Manual Playlist")

        val state = viewModel.uiState.value
        assertEquals("Created Manual Playlist.m3u", state.playlist.playlistMessage)
        assertTrue(state.playlist.manualPlaylistSongs.isEmpty())
        assertTrue(state.scan.discoveredPlaylists.contains(expectedResult))
        assertTrue(mockPlaylistService.writePlaylistCalled)
    }

    @Test
    fun addManyToExistingPlaylist_emptyFiles_returnsEarly() {
        val playlist = PlaylistInfo(uriString = "content://test/playlist.m3u", displayName = "playlist.m3u")

        viewModel.addManyToExistingPlaylist(playlist, emptyList())

        val state = viewModel.uiState.value
        assertEquals(null, state.playlist.playlistMessage)
    }

    @Test
    fun addManyToExistingPlaylist_failure_setsFailureMessage() {
        val playlist = PlaylistInfo(uriString = "content://test/playlist.m3u", displayName = "playlist.m3u")
        val files = listOf(
            MediaFileInfo(uriString = "content://test/song1", displayName = "Song 1", sizeBytes = 1L, title = "Song 1")
        )
        // No output stream registered, so appendToPlaylist will fail.

        viewModel.addManyToExistingPlaylist(playlist, files)

        val state = viewModel.uiState.value
        assertEquals("Failed to update playlist", state.playlist.playlistMessage)
    }

    @Test
    fun addManyToExistingPlaylist_successNotSelected_updatesMessageOnly() {
        val uri = android.net.Uri.parse("content://test/playlist.m3u")
        Shadows.shadowOf(app.contentResolver)
            .registerOutputStream(uri, ByteArrayOutputStream())

        val playlist = PlaylistInfo(uriString = uri.toString(), displayName = "playlist.m3u")
        val files = listOf(
            MediaFileInfo(uriString = "content://test/song1", displayName = "Song 1", sizeBytes = 1L, title = "Song 1")
        )

        // Playlist is not selected.
        viewModel.addManyToExistingPlaylist(playlist, files)

        val state = viewModel.uiState.value
        assertEquals("Added 1 song(s) to playlist", state.playlist.playlistMessage)
        assertTrue(state.playlist.playlistSongs.isEmpty())
    }

    @Test
    fun addManyToExistingPlaylist_successSelected_updatesMessageAndSongs() {
        val uri = android.net.Uri.parse("content://test/playlist.m3u")
        Shadows.shadowOf(app.contentResolver)
            .registerOutputStream(uri, ByteArrayOutputStream())

        val playlist = PlaylistInfo(uriString = uri.toString(), displayName = "playlist.m3u")
        val existingSong = MediaFileInfo(uriString = "content://test/song1", displayName = "Song 1", sizeBytes = 1L, title = "Song 1")
        val newSong = MediaFileInfo(uriString = "content://test/song2", displayName = "Song 2", sizeBytes = 1L, title = "Song 2")
        val duplicateSong = MediaFileInfo(uriString = "content://test/song1", displayName = "Song 1 dup", sizeBytes = 1L, title = "Song 1")

        // Set state to have this playlist selected with one existing song
        val initialState = MainUiState(
            playlist = PlaylistMgmtState(
                selectedPlaylist = playlist,
                playlistSongs = listOf(existingSong)
            ),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, initialState)

        viewModel.addManyToExistingPlaylist(playlist, listOf(newSong, duplicateSong))

        val state = viewModel.uiState.value
        assertEquals("Added 2 song(s) to playlist", state.playlist.playlistMessage)
        assertEquals(listOf(existingSong, newSong), state.playlist.playlistSongs)
    }

    @Test
    fun createPlaylistFromSongs_updatesScanCacheAndMediaCacheService() {
        val treeUri = Uri.parse("content://tree")
        viewModel.setTreeUri(treeUri)
        val files = listOf(MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1"))

        // Seed scanCache via reflection
        val scanCacheField = MainViewModel::class.java.getDeclaredField("scanCache")
        scanCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scanCache = scanCacheField.get(viewModel) as java.util.concurrent.ConcurrentHashMap<String, Pair<List<MediaFileInfo>, List<PlaylistInfo>>>

        val key = "${treeUri}|${viewModel.uiState.value.scan.lastScanLimit}"
        val initialFiles = listOf(MediaFileInfo("content://initial", "initial.mp3", 0L, "Initial"))
        val initialPlaylists = listOf(PlaylistInfo("content://existing", "existing.m3u"))
        scanCache[key] = initialFiles to initialPlaylists

        // Seed mockPlaylistService
        val expectedResult = PlaylistInfo("content://playlist.m3u", "My Playlist.m3u")
        mockPlaylistService.mockResult = expectedResult

        // Call the target method
        val result = viewModel.createPlaylistFromSongs("My Playlist", files)

        // Verify the created playlist returned is correct
        assertEquals(expectedResult, result)

        // Verify scanCache is updated with the new playlist added to the list of playlists
        val updatedCacheEntry = scanCache[key]
        org.junit.Assert.assertNotNull(updatedCacheEntry)
        assertEquals(initialFiles, updatedCacheEntry!!.first)
        assertTrue(updatedCacheEntry.second.contains(expectedResult))
        assertTrue(updatedCacheEntry.second.contains(PlaylistInfo("content://existing", "existing.m3u")))

        // Verify mediaCacheService is updated
        val mediaCacheServiceField = MainViewModel::class.java.getDeclaredField("mediaCacheService")
        mediaCacheServiceField.isAccessible = true
        val mediaCacheService = mediaCacheServiceField.get(viewModel) as com.example.mymediaplayer.shared.MediaCacheService
        assertTrue(mediaCacheService.discoveredPlaylists.contains(expectedResult))
    }

    @Test
    fun createPlaylistFromSongs_withPlaylistTreeUri_resolvesCorrectly() {
        val playlistTreeUri = Uri.parse("content://playlist_tree")
        val playlistTreeUriField = MainViewModel::class.java.getDeclaredField("playlistTreeUri")
        playlistTreeUriField.isAccessible = true
        playlistTreeUriField.set(viewModel, playlistTreeUri)

        val files = listOf(MediaFileInfo("content://song1", "song1.mp3", 0L, "Song 1"))
        val expectedResult = PlaylistInfo("content://playlist.m3u", "My Playlist.m3u")
        mockPlaylistService.mockResult = expectedResult

        val result = viewModel.createPlaylistFromSongs("My Playlist", files)

        assertEquals(expectedResult, result)
        assertTrue(mockPlaylistService.writePlaylistCalled)
    }

    private fun seedUiState(viewModel: MainViewModel, state: MainUiState) {
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<MainUiState>
        flow.value = state
    }
}
