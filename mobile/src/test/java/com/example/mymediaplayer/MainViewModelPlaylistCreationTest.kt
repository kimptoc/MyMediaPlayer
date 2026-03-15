package com.example.mymediaplayer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import com.example.mymediaplayer.shared.PlaylistService
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import org.robolectric.shadows.ShadowContentResolver
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
}
