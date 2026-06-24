package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun loadPersistedCache_rebuildsCacheFromDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = MediaCacheDatabase.getInstance(context)
        val dao = db.cacheDao()
        val treeUri = Uri.parse("content://test/tree")
        val trackUri = "content://test/tree/primary%3AMusic%2Frap%2Ftrack.mp3"

        withContext(Dispatchers.IO) {
            dao.replaceCache(
                files = listOf(
                    MediaFileEntity(
                        uriString = trackUri,
                        displayName = "track.mp3",
                        sizeBytes = 1_000L,
                        title = "Track",
                        artist = "Artist",
                        album = "Album",
                        genre = null,
                        durationMs = 10_000L,
                        year = 1999,
                        addedAtMs = 1_234L
                    )
                ),
                playlists = listOf(PlaylistEntity("content://test/playlist", "Playlist")),
                state = ScanStateEntity(
                    treeUri = treeUri.toString(),
                    scanLimit = 10,
                    scannedAt = 42L
                )
            )
        }

        val service = MediaCacheService()
        val persisted = withContext(Dispatchers.IO) {
            service.loadPersistedCache(context, treeUri, maxFiles = 10)
        }

        assertNotNull(persisted)
        assertEquals(42L, persisted!!.scannedAt)
        assertEquals(1, persisted.files.size)
        assertEquals("Hip-Hop", persisted.files.single().genre)
        assertEquals(persisted.files.single(), service.getFileByUri(trackUri))
        assertEquals(listOf(PlaylistInfo("content://test/playlist", "Playlist")), service.discoveredPlaylists)

        withContext(Dispatchers.IO) {
            dao.clearFiles()
            dao.clearPlaylists()
            dao.clearScanState()
        }
    }

    @Test
    fun loadPersistedCache_loadsAllFilesAcrossMultiplePages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = MediaCacheDatabase.getInstance(context)
        val dao = db.cacheDao()
        val treeUri = Uri.parse("content://test/tree")

        // More than one page at the production page size (500) to exercise paging.
        val fileCount = 1200
        withContext(Dispatchers.IO) {
            dao.replaceCache(
                files = (0 until fileCount).map { i ->
                    MediaFileEntity(
                        uriString = "content://test/song$i",
                        displayName = "Song $i.mp3",
                        sizeBytes = 100L,
                        title = "Song $i",
                        artist = "Artist",
                        album = "Album",
                        genre = "Rock",
                        durationMs = 1000L,
                        year = 2000,
                        addedAtMs = 1L
                    )
                },
                playlists = emptyList(),
                state = ScanStateEntity(treeUri = treeUri.toString(), scanLimit = fileCount, scannedAt = 99L)
            )
        }

        val service = MediaCacheService()
        val persisted = withContext(Dispatchers.IO) {
            service.loadPersistedCache(context, treeUri, maxFiles = fileCount)
        }

        assertNotNull(persisted)
        assertEquals(fileCount, persisted!!.files.size)
        assertEquals(fileCount, service.cachedFilesCount)
        assertEquals(fileCount, persisted.files.map { it.uriString }.distinct().size)

        withContext(Dispatchers.IO) {
            dao.clearFiles()
            dao.clearPlaylists()
            dao.clearScanState()
        }
    }

    @Test
    fun mediaCacheDao_getFilesPage_returnsRequestedSlice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = MediaCacheDatabase.getInstance(context)
        val dao = db.cacheDao()

        withContext(Dispatchers.IO) {
            dao.clearFiles()
            val entities = (0 until 5).map { i ->
                MediaFileEntity(
                    uriString = "content://test/song$i",
                    displayName = "Song $i",
                    sizeBytes = 100L,
                    title = "Song $i",
                    artist = null,
                    album = null,
                    genre = null,
                    durationMs = null,
                    year = null,
                    addedAtMs = null
                )
            }
            dao.insertFiles(entities)
        }

        val count = withContext(Dispatchers.IO) { dao.getFileCount() }
        assertEquals(5, count)

        val firstPage = withContext(Dispatchers.IO) { dao.getFilesPage(limit = 2, offset = 0) }
        val secondPage = withContext(Dispatchers.IO) { dao.getFilesPage(limit = 2, offset = 2) }
        val thirdPage = withContext(Dispatchers.IO) { dao.getFilesPage(limit = 2, offset = 4) }

        assertEquals(2, firstPage.size)
        assertEquals(2, secondPage.size)
        assertEquals(1, thirdPage.size)
        val allUris = (firstPage + secondPage + thirdPage).map { it.uriString }.toSet()
        assertEquals(5, allUris.size)

        withContext(Dispatchers.IO) { dao.clearFiles() }
    }

    @Test
    fun songsForDecade_returnsCorrectSongs() {
        val service = MediaCacheService()

        service.addFile(
            MediaFileInfo(
                uriString = "uri1",
                displayName = "file1",
                sizeBytes = 1000L,
                year = 1995
            )
        )
        service.addFile(
            MediaFileInfo(
                uriString = "uri2",
                displayName = "file2",
                sizeBytes = 1000L,
                year = 1998
            )
        )
        service.addFile(
            MediaFileInfo(
                uriString = "uri3",
                displayName = "file3",
                sizeBytes = 1000L,
                year = 2005
            )
        )
        service.buildAlbumArtistIndexesFromCache()

        val songs90s = service.songsForDecade("1990s")
        assertEquals(2, songs90s.size)
        assertEquals("uri1", songs90s[0].uriString)
        assertEquals("uri2", songs90s[1].uriString)

        val songs2000s = service.songsForDecade("2000s")
        assertEquals(1, songs2000s.size)
        assertEquals("uri3", songs2000s[0].uriString)

        val songsUnknown = service.songsForDecade("1980s")
        assertTrue(songsUnknown.isEmpty())
    }

    @Test
    fun clearCache_emptiesFilesAndPlaylists() {
        val service = MediaCacheService()

        service.addFile(
            MediaFileInfo(
                uriString = "uri1",
                displayName = "file1",
                sizeBytes = 1000L,
                album = "Album",
                year = 1995
            )
        )
        service.addPlaylist(PlaylistInfo("content://playlist1", "Playlist 1"))
        service.buildAlbumArtistIndexesFromCache()

        assertEquals(1, service.cachedFiles.size)
        assertEquals(1, service.discoveredPlaylists.size)
        assertTrue(service.hasAlbumArtistIndexes())
        assertEquals(1, service.albums().size)

        service.clearCache()

        assertTrue(service.cachedFiles.isEmpty())
        assertTrue(service.discoveredPlaylists.isEmpty())
        assertFalse(service.hasAlbumArtistIndexes())
        assertTrue(service.albums().isEmpty())
    }

    @Test
    fun trimMemory_clearsIndexesButKeepsCachedFiles() {
        val service = MediaCacheService()

        service.addFile(
            MediaFileInfo(
                uriString = "uri1",
                displayName = "file1",
                sizeBytes = 1000L,
                album = "Album",
                year = 1995
            )
        )
        service.buildAlbumArtistIndexesFromCache()
        service.cachedMusicFiles

        assertTrue(service.hasAlbumArtistIndexes())
        assertEquals(1, service.cachedFiles.size)

        service.trimMemory()

        assertFalse(service.hasAlbumArtistIndexes())
        assertEquals(1, service.cachedFiles.size)
        assertEquals(1, service.cachedMusicFiles.size)
    }

    @Test
    fun hasCachedFiles_reflectsCacheStateWithoutCopying() {
        val service = MediaCacheService()
        assertFalse(service.hasCachedFiles())

        service.addFile(MediaFileInfo(uriString = "content://test/song1", displayName = "Song 1", sizeBytes = 100L, title = "Song 1"))
        assertTrue(service.hasCachedFiles())

        service.clearCache()
        assertFalse(service.hasCachedFiles())
    }

    @Test
    fun cachedFilesCount_matchesCachedFilesSize() {
        val service = MediaCacheService()
        assertEquals(0, service.cachedFilesCount)

        service.addFile(MediaFileInfo(uriString = "content://test/song1", displayName = "Song 1", sizeBytes = 100L, title = "Song 1"))
        service.addFile(MediaFileInfo(uriString = "content://test/song2", displayName = "Song 2", sizeBytes = 100L, title = "Song 2"))

        assertEquals(2, service.cachedFilesCount)
        assertEquals(service.cachedFiles.size, service.cachedFilesCount)
    }

    // Regression tests for issue #382 (OOM crash loop persisting after #370). This
    // breadcrumb is the only diagnostic that survives a release build (Log.d/Timber
    // are no-ops there) *and* the only one that can survive an actual OOM, since it
    // writes before the risky allocation instead of from inside a catch block (which
    // would itself need to allocate while the heap is already exhausted).
    @Test
    fun recordLoadStarted_writesBreadcrumbBeforeAllocating() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(MediaCacheService.OOM_DIAGNOSTIC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val service = MediaCacheService()

        service.recordLoadStarted(context, totalFiles = 14209)

        val prefs = context.getSharedPreferences(MediaCacheService.OOM_DIAGNOSTIC_PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("load_in_progress", false))
        assertEquals(14209, prefs.getInt("load_total_files", -1))
        assertEquals(1, prefs.getInt("load_concurrent_entries", -1))
        assertTrue(prefs.getLong("load_started_at_ms", -1L) > 0L)
        assertTrue(prefs.getLong("load_started_max_heap_kb", -1L) > 0L)
    }

    @Test
    fun recordLoadFinished_clearsInProgressFlag() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = MediaCacheService()
        service.recordLoadStarted(context, totalFiles = 100)

        service.recordLoadFinished(context)

        val prefs = context.getSharedPreferences(MediaCacheService.OOM_DIAGNOSTIC_PREFS_NAME, Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean("load_in_progress", true))
    }

    @Test
    fun recordLoadStarted_countsConcurrentEntriesAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val phoneInstance = MediaCacheService()
        val serviceInstance = MediaCacheService()
        val prefs = context.getSharedPreferences(MediaCacheService.OOM_DIAGNOSTIC_PREFS_NAME, Context.MODE_PRIVATE)

        try {
            phoneInstance.recordLoadStarted(context, totalFiles = 14209)
            assertEquals(1, prefs.getInt("load_concurrent_entries", -1))

            serviceInstance.recordLoadStarted(context, totalFiles = 14209)
            assertEquals(
                "A second concurrent load (e.g. phone + service both loading at once) " +
                    "must be visible in the breadcrumb regardless of which instance started it",
                2,
                prefs.getInt("load_concurrent_entries", -1)
            )
        } finally {
            phoneInstance.recordLoadFinished(context)
            serviceInstance.recordLoadFinished(context)
        }
    }
}
