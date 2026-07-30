package com.example.mymediaplayer

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaCacheDatabase
import com.example.mymediaplayer.shared.MediaCacheService
import com.example.mymediaplayer.shared.MediaFileEntity
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistEntity
import com.example.mymediaplayer.shared.PlaylistInfo
import com.example.mymediaplayer.shared.ScanStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelOnDirectorySelectedTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel
    private lateinit var mediaCacheService: MediaCacheService
    private lateinit var scanCache: ConcurrentHashMap<String, Pair<List<MediaFileInfo>, List<PlaylistInfo>>>

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(app)

        // Extract mediaCacheService via reflection
        val mediaCacheField = viewModel.javaClass.getDeclaredField("mediaCacheService")
        mediaCacheField.isAccessible = true
        mediaCacheService = mediaCacheField.get(viewModel) as MediaCacheService

        // Extract scanCache via reflection
        val scanCacheField = viewModel.javaClass.getDeclaredField("scanCache")
        scanCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        scanCache = scanCacheField.get(viewModel) as ConcurrentHashMap<String, Pair<List<MediaFileInfo>, List<PlaylistInfo>>>

        // Clear shared preferences and Room database
        app.getSharedPreferences("mymediaplayer_prefs", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val db = MediaCacheDatabase.getInstance(app)
        val dao = db.cacheDao()
        runBlocking {
            withContext(Dispatchers.IO) {
                dao.clearFiles()
                dao.clearPlaylists()
                dao.clearScanState()
            }
        }
    }

    private fun awaitCondition(timeoutMs: Long = 2000L, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition() && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(10)
        }
        if (!condition()) throw AssertionError("Condition was not met within ${timeoutMs}ms")
    }

    @Test
    fun onDirectorySelected_memoryCacheHit_loadsCachedDataImmediately() {
        val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri("test", "root")
        val maxFiles = 10
        val key = viewModel.buildScanCacheKey(treeUri, maxFiles, deepScan = false)

        val cachedFiles = listOf(
            MediaFileInfo(
                uriString = "content://test/tree/song.mp3",
                displayName = "song.mp3",
                sizeBytes = 100L,
                title = "Cached Song"
            )
        )
        val cachedPlaylists = listOf(
            PlaylistInfo(
                uriString = "content://test/tree/playlist.m3u",
                displayName = "playlist.m3u"
            )
        )

        // Seed the memory scan cache
        scanCache[key] = cachedFiles to cachedPlaylists

        // Set an initial different UI state
        val initialUiState = MainUiState(
            scan = ScanState(
                isScanning = false,
                scannedFiles = emptyList(),
                discoveredPlaylists = emptyList(),
                lastScanLimit = 0
            ),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, initialUiState)

        // Invoke method
        viewModel.onDirectorySelected(treeUri, maxFiles, deepScan = false, forceRescan = false)

        // Since memory cache is a synchronous return, the state should be updated immediately
        val state = viewModel.uiState.value
        assertEquals(cachedFiles, state.scan.scannedFiles)
        assertEquals(cachedPlaylists, state.scan.discoveredPlaylists)
        assertEquals(maxFiles, state.scan.lastScanLimit)
        assertFalse(state.scan.isScanning)

        // Check MediaCacheService as well
        assertEquals(cachedFiles, mediaCacheService.cachedFiles)
        assertEquals(cachedPlaylists, mediaCacheService.discoveredPlaylists)
    }

    @Test
    fun onDirectorySelected_diskCacheHit_loadsPersistedData() = runBlocking {
        val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri("test", "root")
        val maxFiles = 10
        val key = viewModel.buildScanCacheKey(treeUri, maxFiles, deepScan = false)

        val trackUri = "content://test/tree/song.mp3"
        val playlistUri = "content://test/tree/playlist.m3u"

        // Seed Room database
        val db = MediaCacheDatabase.getInstance(app)
        val dao = db.cacheDao()
        withContext(Dispatchers.IO) {
            dao.replaceCache(
                files = listOf(
                    MediaFileEntity(
                        uriString = trackUri,
                        displayName = "song.mp3",
                        sizeBytes = 100L,
                        title = "Persisted Song",
                        artist = "Artist",
                        album = "Album",
                        genre = "Rock",
                        durationMs = 5000L,
                        year = 2020,
                        addedAtMs = 12345L
                    )
                ),
                playlists = listOf(
                    PlaylistEntity(
                        uriString = playlistUri,
                        displayName = "playlist.m3u"
                    )
                ),
                state = ScanStateEntity(
                    treeUri = treeUri.toString(),
                    scanLimit = maxFiles,
                    scannedAt = System.currentTimeMillis()
                )
            )
        }

        // Set an initial different UI state
        val initialUiState = MainUiState(
            scan = ScanState(
                isScanning = false,
                scannedFiles = emptyList(),
                discoveredPlaylists = emptyList(),
                lastScanLimit = 0
            ),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, initialUiState)

        // Invoke method
        viewModel.onDirectorySelected(treeUri, maxFiles, deepScan = false, forceRescan = false)

        // Wait for the asynchronous disk load coroutine to complete
        awaitCondition {
            viewModel.uiState.value.scan.scannedFiles.isNotEmpty()
        }

        val state = viewModel.uiState.value
        assertEquals(1, state.scan.scannedFiles.size)
        assertEquals(trackUri, state.scan.scannedFiles[0].uriString)
        assertEquals("Persisted Song", state.scan.scannedFiles[0].title)

        assertEquals(1, state.scan.discoveredPlaylists.size)
        assertEquals(playlistUri, state.scan.discoveredPlaylists[0].uriString)

        assertFalse(state.scan.isScanning)

        // Check scanCache is also updated
        val cached = scanCache[key]
        assertTrue(cached != null)
        assertEquals(trackUri, cached!!.first[0].uriString)
    }

    @Test
    fun onDirectorySelected_emptyScan_performsDirectoryScan() {
        val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri("test", "root")
        val maxFiles = 10

        // Set an initial different UI state
        val initialUiState = MainUiState(
            scan = ScanState(
                isScanning = false,
                scannedFiles = emptyList(),
                discoveredPlaylists = emptyList(),
                lastScanLimit = 0
            ),
            isPreferencesLoading = false
        )
        seedUiState(viewModel, initialUiState)

        // Invoke method (forceRescan = true, will bypass any memory/disk cache checks and run scanning)
        viewModel.onDirectorySelected(treeUri, maxFiles, deepScan = false, forceRescan = true)

        // Wait for scan to complete (isScanning turns false)
        awaitCondition {
            !viewModel.uiState.value.scan.isScanning && viewModel.uiState.value.scan.scanMessage != null
        }

        val state = viewModel.uiState.value
        assertFalse(state.scan.isScanning)
        assertTrue(state.scan.scannedFiles.isEmpty())
        assertTrue(state.scan.discoveredPlaylists.isEmpty())
        assertTrue(state.scan.scanMessage!!.contains("Normal scan complete"))
        assertTrue(state.scan.scanMessage!!.contains("Songs: 0"))
    }

    private fun seedUiState(viewModel: MainViewModel, state: MainUiState) {
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<MainUiState>
        flow.value = state
    }
}
