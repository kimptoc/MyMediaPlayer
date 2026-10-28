package com.example.mymediaplayer.shared

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCacheDatabaseMigrationTest {

    @Test
    fun migration3to4_keepsRowsAndAddsFlagsThatRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_3_4_test.db"
        context.deleteDatabase(name)

        // The version 3 schema, as Room created it.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(
                "CREATE TABLE media_files (uriString TEXT NOT NULL, displayName TEXT NOT NULL, " +
                    "sizeBytes INTEGER NOT NULL, title TEXT, artist TEXT, album TEXT, genre TEXT, " +
                    "durationMs INTEGER, year INTEGER, addedAtMs INTEGER, PRIMARY KEY(uriString))"
            )
            db.execSQL(
                "CREATE TABLE playlists (uriString TEXT NOT NULL, displayName TEXT NOT NULL, " +
                    "PRIMARY KEY(uriString))"
            )
            db.execSQL(
                "CREATE TABLE scan_state (id INTEGER NOT NULL, treeUri TEXT NOT NULL, " +
                    "scanLimit INTEGER NOT NULL, scannedAt INTEGER NOT NULL, PRIMARY KEY(id))"
            )
            db.execSQL(
                "INSERT INTO media_files (uriString, displayName, sizeBytes) " +
                    "VALUES ('content://media/external/audio/media/1', 'old.mp3', 1)"
            )
            db.version = 3
        }

        // No destructive fallback here, so a bad migration fails the test.
        val room = Room.databaseBuilder(context, MediaCacheDatabase::class.java, name)
            .addMigrations(MediaCacheDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = room.cacheDao()
            val old = dao.getAllFiles().single()
            assertEquals("old.mp3", old.displayName)
            assertFalse(old.isPodcast)
            assertFalse(old.isRecording)

            dao.insertFiles(listOf(old.copy(uriString = "content://media/external/audio/media/2", isRecording = true)))
            val saved = dao.getAllFiles().single { it.uriString.endsWith("/2") }
            assertTrue(saved.isRecording)
            assertFalse(saved.isPodcast)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
