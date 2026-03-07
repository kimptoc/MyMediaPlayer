package com.example.mymediaplayer.shared

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCacheDatabaseTest {

    private lateinit var db: MediaCacheDatabase
    private lateinit var dao: MediaCacheDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            MediaCacheDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.cacheDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @org.junit.Test
    fun insertAndGetMediaFiles() {
        val file1 = MediaFileEntity(
            uriString = "content://media/1",
            displayName = "song1.mp3",
            sizeBytes = 1000L,
            title = "Song One",
            artist = "Artist A",
            album = "Album 1",
            genre = "Rock",
            durationMs = 200000L,
            year = 2020,
            addedAtMs = 1600000000L
        )
        val file2 = MediaFileEntity(
            uriString = "content://media/2",
            displayName = "song2.mp3",
            sizeBytes = 2000L,
            title = "Song Two",
            artist = "Artist B",
            album = "Album 2",
            genre = "Pop",
            durationMs = 180000L,
            year = 2021,
            addedAtMs = 1610000000L
        )

        dao.insertFiles(listOf(file1, file2))

        val files = dao.getAllFiles()
        org.junit.Assert.assertEquals(2, files.size)
        org.junit.Assert.assertTrue(files.contains(file1))
        org.junit.Assert.assertTrue(files.contains(file2))
    }

    @org.junit.Test
    fun clearMediaFiles() {
        val file1 = MediaFileEntity(
            uriString = "content://media/1",
            displayName = "song1.mp3",
            sizeBytes = 1000L,
            title = null,
            artist = null,
            album = null,
            genre = null,
            durationMs = null,
            year = null,
            addedAtMs = null
        )

        dao.insertFiles(listOf(file1))
        var files = dao.getAllFiles()
        org.junit.Assert.assertEquals(1, files.size)

        dao.clearFiles()
        files = dao.getAllFiles()
        org.junit.Assert.assertTrue(files.isEmpty())
    }

    @org.junit.Test
    fun insertAndGetPlaylists() {
        val playlist1 = PlaylistEntity(
            uriString = "content://playlist/1",
            displayName = "Favorites"
        )
        val playlist2 = PlaylistEntity(
            uriString = "content://playlist/2",
            displayName = "Workout"
        )

        dao.insertPlaylists(listOf(playlist1, playlist2))

        val playlists = dao.getAllPlaylists()
        org.junit.Assert.assertEquals(2, playlists.size)
        org.junit.Assert.assertTrue(playlists.contains(playlist1))
        org.junit.Assert.assertTrue(playlists.contains(playlist2))
    }

    @org.junit.Test
    fun clearPlaylists() {
        val playlist = PlaylistEntity(
            uriString = "content://playlist/1",
            displayName = "Favorites"
        )

        dao.insertPlaylists(listOf(playlist))
        var playlists = dao.getAllPlaylists()
        org.junit.Assert.assertEquals(1, playlists.size)

        dao.clearPlaylists()
        playlists = dao.getAllPlaylists()
        org.junit.Assert.assertTrue(playlists.isEmpty())
    }

    @org.junit.Test
    fun upsertAndGetScanState() {
        val state1 = ScanStateEntity(
            id = 0,
            treeUri = "content://tree/1",
            scanLimit = 100,
            scannedAt = 1600000000L
        )

        dao.upsertScanState(state1)
        var retrievedState = dao.getScanState()
        org.junit.Assert.assertEquals(state1, retrievedState)

        val state2 = ScanStateEntity(
            id = 0,
            treeUri = "content://tree/2",
            scanLimit = 200,
            scannedAt = 1610000000L
        )

        dao.upsertScanState(state2)
        retrievedState = dao.getScanState()
        org.junit.Assert.assertEquals(state2, retrievedState)
    }

    @org.junit.Test
    fun clearScanState() {
        val state = ScanStateEntity(
            id = 0,
            treeUri = "content://tree/1",
            scanLimit = 100,
            scannedAt = 1600000000L
        )

        dao.upsertScanState(state)
        var retrievedState = dao.getScanState()
        org.junit.Assert.assertNotNull(retrievedState)

        dao.clearScanState()
        retrievedState = dao.getScanState()
        org.junit.Assert.assertNull(retrievedState)
    }

    @org.junit.Test
    fun replaceCache() {
        val oldFile = MediaFileEntity(
            uriString = "content://old/1",
            displayName = "old.mp3",
            sizeBytes = 1000L,
            title = null, artist = null, album = null, genre = null, durationMs = null, year = null, addedAtMs = null
        )
        val oldPlaylist = PlaylistEntity(
            uriString = "content://old_playlist/1",
            displayName = "Old Playlist"
        )
        val oldState = ScanStateEntity(
            id = 0,
            treeUri = "content://old_tree/1",
            scanLimit = 100,
            scannedAt = 1600000000L
        )

        dao.insertFiles(listOf(oldFile))
        dao.insertPlaylists(listOf(oldPlaylist))
        dao.upsertScanState(oldState)

        val newFile = MediaFileEntity(
            uriString = "content://new/1",
            displayName = "new.mp3",
            sizeBytes = 2000L,
            title = null, artist = null, album = null, genre = null, durationMs = null, year = null, addedAtMs = null
        )
        val newPlaylist = PlaylistEntity(
            uriString = "content://new_playlist/1",
            displayName = "New Playlist"
        )
        val newState = ScanStateEntity(
            id = 0,
            treeUri = "content://new_tree/1",
            scanLimit = 200,
            scannedAt = 1610000000L
        )

        dao.replaceCache(listOf(newFile), listOf(newPlaylist), newState)

        val files = dao.getAllFiles()
        org.junit.Assert.assertEquals(1, files.size)
        org.junit.Assert.assertEquals(newFile, files[0])

        val playlists = dao.getAllPlaylists()
        org.junit.Assert.assertEquals(1, playlists.size)
        org.junit.Assert.assertEquals(newPlaylist, playlists[0])

        val retrievedState = dao.getScanState()
        org.junit.Assert.assertEquals(newState, retrievedState)
    }

    @org.junit.Test
    fun replaceCache_emptyLists() {
        val oldFile = MediaFileEntity(
            uriString = "content://old/1",
            displayName = "old.mp3",
            sizeBytes = 1000L,
            title = null, artist = null, album = null, genre = null, durationMs = null, year = null, addedAtMs = null
        )
        val oldPlaylist = PlaylistEntity(
            uriString = "content://old_playlist/1",
            displayName = "Old Playlist"
        )
        val oldState = ScanStateEntity(
            id = 0,
            treeUri = "content://old_tree/1",
            scanLimit = 100,
            scannedAt = 1600000000L
        )

        dao.insertFiles(listOf(oldFile))
        dao.insertPlaylists(listOf(oldPlaylist))
        dao.upsertScanState(oldState)

        val newState = ScanStateEntity(
            id = 0,
            treeUri = "content://new_tree/1",
            scanLimit = 200,
            scannedAt = 1610000000L
        )

        dao.replaceCache(emptyList(), emptyList(), newState)

        val files = dao.getAllFiles()
        org.junit.Assert.assertTrue(files.isEmpty())

        val playlists = dao.getAllPlaylists()
        org.junit.Assert.assertTrue(playlists.isEmpty())

        val retrievedState = dao.getScanState()
        org.junit.Assert.assertEquals(newState, retrievedState)
    }
}
