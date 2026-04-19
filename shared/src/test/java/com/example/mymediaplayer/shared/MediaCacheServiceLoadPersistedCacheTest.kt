package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCacheServiceLoadPersistedCacheTest {

    private lateinit var context: Context
    private lateinit var db: MediaCacheDatabase
    private lateinit var dao: MediaCacheDao
    private lateinit var service: MediaCacheService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            MediaCacheDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.cacheDao()

        // Inject the in-memory database into the MediaCacheDatabase singleton
        // to ensure MediaCacheService uses it
        ReflectionHelpers.setStaticField(MediaCacheDatabase::class.java, "instance", db)

        service = MediaCacheService()
    }

    @After
    fun teardown() {
        db.close()
        ReflectionHelpers.setStaticField(MediaCacheDatabase::class.java, "instance", null)
    }

    @Test
    fun loadPersistedCache_returnsNullWhenNoState() {
        val treeUri = Uri.parse("content://tree/test")
        val maxFiles = 100

        val result = service.loadPersistedCache(context, treeUri, maxFiles)

        assertNull("Should return null when scan state is missing", result)
    }

    @Test
    fun loadPersistedCache_returnsNullWhenTreeUriMismatches() {
        val maxFiles = 100
        val state = ScanStateEntity(
            treeUri = "content://tree/old",
            scanLimit = maxFiles,
            scannedAt = 12345L
        )
        dao.upsertScanState(state)

        val newTreeUri = Uri.parse("content://tree/new")
        val result = service.loadPersistedCache(context, newTreeUri, maxFiles)

        assertNull("Should return null when tree Uri does not match", result)
    }

    @Test
    fun loadPersistedCache_returnsNullWhenMaxFilesMismatches() {
        val treeUriStr = "content://tree/test"
        val state = ScanStateEntity(
            treeUri = treeUriStr,
            scanLimit = 100,
            scannedAt = 12345L
        )
        dao.upsertScanState(state)

        val treeUri = Uri.parse(treeUriStr)
        val result = service.loadPersistedCache(context, treeUri, 200)

        assertNull("Should return null when max files do not match", result)
    }

    @Test
    fun loadPersistedCache_loadsDataSuccessfully() {
        val treeUriStr = "content://tree/test"
        val maxFiles = 100
        val state = ScanStateEntity(
            treeUri = treeUriStr,
            scanLimit = maxFiles,
            scannedAt = 12345L
        )
        dao.upsertScanState(state)

        val file1 = MediaFileEntity(
            uriString = "content://file/1",
            displayName = "song1.mp3",
            sizeBytes = 1024L,
            title = "Song One",
            artist = "Artist A",
            album = "Album 1",
            genre = "Rock",
            durationMs = 2000L,
            year = 2020,
            addedAtMs = 123456L
        )
        dao.insertFiles(listOf(file1))

        val playlist1 = PlaylistEntity(
            uriString = "content://playlist/1",
            displayName = "My Playlist"
        )
        dao.insertPlaylists(listOf(playlist1))

        val treeUri = Uri.parse(treeUriStr)
        val result = service.loadPersistedCache(context, treeUri, maxFiles)

        assertNotNull("Should not return null on successful load", result)
        assertEquals("Scanned at should match", 12345L, result?.scannedAt)

        assertEquals("Should load one file", 1, result?.files?.size)
        val loadedFile = result?.files?.get(0)
        assertEquals("content://file/1", loadedFile?.uriString)
        assertEquals("song1.mp3", loadedFile?.displayName)
        assertEquals("Rock", loadedFile?.genre)

        assertEquals("Should load one playlist", 1, result?.playlists?.size)
        val loadedPlaylist = result?.playlists?.get(0)
        assertEquals("content://playlist/1", loadedPlaylist?.uriString)
        assertEquals("My Playlist", loadedPlaylist?.displayName)

        assertEquals("Cache should contain the loaded file", 1, service.cachedFiles.size)
        assertEquals("content://file/1", service.cachedFiles[0].uriString)

        assertEquals("Discovered playlists should contain the loaded playlist", 1, service.discoveredPlaylists.size)
        assertEquals("content://playlist/1", service.discoveredPlaylists[0].uriString)
    }
}
