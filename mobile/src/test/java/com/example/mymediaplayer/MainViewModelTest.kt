package com.example.mymediaplayer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
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
}
