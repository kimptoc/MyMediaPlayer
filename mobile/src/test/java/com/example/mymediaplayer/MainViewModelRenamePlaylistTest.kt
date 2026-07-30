package com.example.mymediaplayer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import com.example.mymediaplayer.shared.PlaylistService
import com.example.mymediaplayer.shared.MediaCacheService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field
import kotlinx.coroutines.flow.MutableStateFlow

class MockRenamePlaylistService : PlaylistService() {
    var renamePlaylistResult: PlaylistInfo? = null
    var readPlaylistResult: List<MediaFileInfo> = emptyList()
    var writePlaylistWithNameResult: PlaylistInfo? = null
    var deletePlaylistResult: Boolean = true

    var renamePlaylistCalled = false
    var readPlaylistCalled = false
    var writePlaylistWithNameCalled = false
    var deletePlaylistCalled = false

    var passedRenameUri: Uri? = null
    var passedRenameName: String? = null
    var passedReadUri: Uri? = null
    var passedWriteTree: Uri? = null
    var passedWriteFiles: List<MediaFileInfo>? = null
    var passedWriteName: String? = null
    var passedDeleteUri: Uri? = null

    override fun renamePlaylist(context: Context, playlistUri: Uri, newName: String): PlaylistInfo? {
        renamePlaylistCalled = true
        passedRenameUri = playlistUri
        passedRenameName = newName
        return renamePlaylistResult
    }

    override fun readPlaylist(context: Context, playlistUri: Uri): List<MediaFileInfo> {
        readPlaylistCalled = true
        passedReadUri = playlistUri
        return readPlaylistResult
    }

    override fun writePlaylistWithName(
        context: Context,
        treeUri: Uri,
        files: List<MediaFileInfo>,
        name: String
    ): PlaylistInfo? {
        writePlaylistWithNameCalled = true
        passedWriteTree = treeUri
        passedWriteFiles = files
        passedWriteName = name
        return writePlaylistWithNameResult
    }

    override fun deletePlaylist(
        context: Context,
        playlistUri: Uri,
        displayName: String?,
        treeUri: Uri?
    ): Boolean {
        deletePlaylistCalled = true
        passedDeleteUri = playlistUri
        return deletePlaylistResult
    }
}

@RunWith(RobolectricTestRunner::class)
class MainViewModelRenamePlaylistTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel
    private lateinit var mockService: MockRenamePlaylistService

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(app)
        mockService = MockRenamePlaylistService()

        val field: Field = MainViewModel::class.java.getDeclaredField("playlistService")
        field.isAccessible = true
        field.set(viewModel, mockService)

        app.getSharedPreferences("mymediaplayer_prefs", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun renamePlaylist_success_directRename() {
        val originalPlaylist = PlaylistInfo("content://test/playlist1.m3u", "playlist1.m3u")
        val renamedPlaylist = PlaylistInfo("content://test/playlist1_new.m3u", "playlist1_new.m3u")
        mockService.renamePlaylistResult = renamedPlaylist

        viewModel.renamePlaylist(originalPlaylist, "playlist1_new")

        assertTrue(mockService.renamePlaylistCalled)
        assertEquals(Uri.parse("content://test/playlist1.m3u"), mockService.passedRenameUri)
        assertEquals("playlist1_new", mockService.passedRenameName)
        assertFalse(mockService.readPlaylistCalled)
        assertFalse(mockService.writePlaylistWithNameCalled)

        val state = viewModel.uiState.value
        assertEquals("Renamed to playlist1_new", state.playlist.playlistMessage)
    }

    @Test
    fun renamePlaylist_fallbackSuccess() {
        val originalPlaylist = PlaylistInfo("content://test/playlist1.m3u", "playlist1.m3u")
        val recreatedPlaylist = PlaylistInfo("content://test/playlist1_new.m3u", "playlist1_new.m3u")
        mockService.renamePlaylistResult = null
        viewModel.setTreeUri(Uri.parse("content://tree"))
        mockService.writePlaylistWithNameResult = recreatedPlaylist
        mockService.deletePlaylistResult = true

        viewModel.renamePlaylist(originalPlaylist, "playlist1_new")

        assertTrue(mockService.renamePlaylistCalled)
        assertTrue(mockService.readPlaylistCalled)
        assertTrue(mockService.writePlaylistWithNameCalled)
        assertTrue(mockService.deletePlaylistCalled)
        assertEquals(Uri.parse("content://test/playlist1.m3u"), mockService.passedDeleteUri)

        val state = viewModel.uiState.value
        assertEquals("Renamed to playlist1_new", state.playlist.playlistMessage)
    }

    @Test
    fun renamePlaylist_fallbackDeleteOldFailed() {
        val originalPlaylist = PlaylistInfo("content://test/playlist1.m3u", "playlist1.m3u")
        val recreatedPlaylist = PlaylistInfo("content://test/playlist1_new.m3u", "playlist1_new.m3u")
        mockService.renamePlaylistResult = null
        viewModel.setTreeUri(Uri.parse("content://tree"))
        mockService.writePlaylistWithNameResult = recreatedPlaylist
        mockService.deletePlaylistResult = false

        viewModel.renamePlaylist(originalPlaylist, "playlist1_new")

        assertTrue(mockService.renamePlaylistCalled)
        assertTrue(mockService.readPlaylistCalled)
        assertTrue(mockService.writePlaylistWithNameCalled)
        assertTrue(mockService.deletePlaylistCalled)

        val state = viewModel.uiState.value
        assertEquals("Renamed, but couldn't delete old file", state.playlist.playlistMessage)
    }

    @Test
    fun renamePlaylist_failure_nullResult() {
        val originalPlaylist = PlaylistInfo("content://test/playlist1.m3u", "playlist1.m3u")
        mockService.renamePlaylistResult = null

        viewModel.renamePlaylist(originalPlaylist, "playlist1_new")

        assertTrue(mockService.renamePlaylistCalled)
        assertFalse(mockService.readPlaylistCalled)

        val state = viewModel.uiState.value
        assertEquals("Failed to rename playlist", state.playlist.playlistMessage)
    }

    @Test
    fun renamePlaylist_updatesStateAndSelectedPlaylist() {
        val originalPlaylist = PlaylistInfo("content://test/playlist1.m3u", "playlist1.m3u")
        val otherPlaylist = PlaylistInfo("content://test/playlist2.m3u", "playlist2.m3u")
        val renamedPlaylist = PlaylistInfo("content://test/playlist1_new.m3u", "playlist1_new.m3u")
        mockService.renamePlaylistResult = renamedPlaylist

        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<MainUiState>
        flow.value = flow.value.copy(
            scan = flow.value.scan.copy(
                discoveredPlaylists = listOf(originalPlaylist, otherPlaylist)
            ),
            playlist = flow.value.playlist.copy(
                selectedPlaylist = originalPlaylist
            )
        )

        viewModel.renamePlaylist(originalPlaylist, "playlist1_new")

        val state = viewModel.uiState.value
        assertEquals(renamedPlaylist, state.playlist.selectedPlaylist)
        assertEquals(2, state.scan.discoveredPlaylists.size)
        assertTrue(state.scan.discoveredPlaylists.contains(renamedPlaylist))
        assertTrue(state.scan.discoveredPlaylists.contains(otherPlaylist))
        assertFalse(state.scan.discoveredPlaylists.contains(originalPlaylist))
    }
}
