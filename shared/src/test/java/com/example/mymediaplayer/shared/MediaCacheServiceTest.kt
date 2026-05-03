package com.example.mymediaplayer.shared

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCacheServiceTest {

    @Test
    fun scanDirectory_reportsProgressEvenWhenEmpty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = DocumentsContract.buildTreeDocumentUri("test", "root")
        val service = MediaCacheService()
        val progress = mutableListOf<Pair<Int, Int>>()

        service.scanDirectory(ScanContext(context, treeUri, maxFiles = 10, onProgress = { songsFound, foldersScanned ->
            progress += songsFound to foldersScanned
        }))

        assertTrue(service.cachedFiles.size <= 10)
        assertTrue(progress.isNotEmpty())
        val last = progress.last()
        assertEquals(service.cachedFiles.size, last.first)
        assertTrue(last.second >= 0)
    }

    @Test
    fun scanDirectory_canBeCancelled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = DocumentsContract.buildTreeDocumentUri("test", "root")
        val service = MediaCacheService()

        val job: Job = launch {
            service.scanDirectory(ScanContext(context, treeUri, maxFiles = 10, onProgress = { _, _ -> }))
        }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
    }

    @Test
    fun albumsByLatestAddedDesc_sortsByNewestTrackPerAlbum() {
        val service = MediaCacheService()
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/a1",
                displayName = "a1.mp3",
                sizeBytes = 1L,
                album = "Album A",
                addedAtMs = 1_000L
            )
        )
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/b1",
                displayName = "b1.mp3",
                sizeBytes = 1L,
                album = "Album B",
                addedAtMs = 3_000L
            )
        )
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/a2",
                displayName = "a2.mp3",
                sizeBytes = 1L,
                album = "Album A",
                addedAtMs = 5_000L
            )
        )
        service.buildAlbumArtistIndexesFromCache()

        val albums = service.albumsByLatestAddedDesc()

        assertEquals(listOf("Album A", "Album B"), albums)
    }

    @Test
    fun isSupportedAudioFile_acceptsMoreExtensionsAndAudioMime() {
        val service = MediaCacheService()

        assertTrue(service.isSupportedAudioFile("track.wma", null))
        assertTrue(service.isSupportedAudioFile("track.alac", null))
        assertTrue(service.isSupportedAudioFile("track.ape", null))
        assertTrue(service.isSupportedAudioFile("track.m4a", "audio/mp4"))
        assertTrue(service.isSupportedAudioFile("track.bin", "audio/flac"))
        assertTrue(service.isSupportedAudioFile("track.unknown", "application/ogg"))
    }

    @Test
    fun isSupportedAudioFile_rejectsPlaylistMime() {
        val service = MediaCacheService()

        assertTrue(service.isSupportedPlaylistFile("playlist.m3u", null))
        assertTrue(service.isSupportedPlaylistFile("playlist.txt", "audio/x-mpegurl"))
        assertFalse(service.isSupportedAudioFile("playlist.m3u", "audio/x-mpegurl"))
    }

    @Test
    fun persistPlaylists_savesToDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = MediaCacheDatabase.getInstance(context)
        val dao = db.cacheDao()

        withContext(Dispatchers.IO) {
            dao.clearPlaylists()
        }

        val service = MediaCacheService()

        service.addPlaylist(PlaylistInfo("content://playlist1", "My Playlist"))
        service.addPlaylist(PlaylistInfo("content://playlist2", "Rock"))

        withContext(Dispatchers.IO) {
            service.persistPlaylists(context)
        }

        val saved = withContext(Dispatchers.IO) {
            dao.getAllPlaylists()
        }

        assertEquals(2, saved.size)
        val sortedSaved = saved.sortedBy { it.displayName }
        assertEquals("content://playlist1", sortedSaved[0].uriString)
        assertEquals("My Playlist", sortedSaved[0].displayName)
        assertEquals("content://playlist2", sortedSaved[1].uriString)
        assertEquals("Rock", sortedSaved[1].displayName)

        withContext(Dispatchers.IO) {
            dao.clearPlaylists()
        }
    }
}
