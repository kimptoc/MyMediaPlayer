package com.example.mymediaplayer.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// #530: the folder scan and the whole-device scan hold the same kinds of audio.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryFoldersTest {

    @Test
    fun topLevelSystemSoundAndRecordingsFoldersAreLeftOut() {
        for (id in listOf("primary:Alarms", "primary:Notifications", "primary:Ringtones", "primary:Recordings",
            "1234-ABCD:recordings/")) {
            assertTrue(id, isNonLibraryFolder(id))
        }
        for (id in listOf("primary:Music", "primary:Sounds", "primary:Podcasts", "primary:My Ringtones Mix",
            "primary:Music/Recordings", "primary:", "Recordings", null)) {
            assertFalse("$id", isNonLibraryFolder(id))
        }
    }

    @Test
    fun wholeDeviceScanReadsMusicPodcastsAndAudiobooks() {
        assertEquals("(is_music != 0 OR is_podcast != 0)", wholeDeviceAudioSelection(Build.VERSION_CODES.P))
        assertEquals(
            "(is_music != 0 OR is_podcast != 0 OR is_audiobook != 0)",
            wholeDeviceAudioSelection(Build.VERSION_CODES.Q)
        )
    }

    @Test
    fun folderScan_skipsSystemFolders_andUsesTheWholePathForPodcasts() = runBlocking {
        val providerInfo = ProviderInfo().apply { authority = FakeTreeProvider.AUTHORITY }
        Robolectric.buildContentProvider(FakeTreeProvider::class.java).create(providerInfo)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = DocumentsContract.buildTreeDocumentUri(FakeTreeProvider.AUTHORITY, "primary:")
        val service = MediaCacheService()

        val stats = service.scanDirectory(ScanContext(context, treeUri, maxFiles = 50))

        val byName = service.cachedFiles.associateBy { it.displayName }
        assertEquals(setOf("song.mp3", "live.mp3", "calm.mp3", "memo.m4a"), byName.keys)
        assertTrue(byName.getValue("song.mp3").isMusic)
        assertTrue(byName.getValue("calm.mp3").isPodcast)
        assertTrue(byName.getValue("memo.m4a").isRecording)
        assertEquals(2, stats.skippedReasons["non_library_folder"])
    }

    class FakeTreeProvider : ContentProvider() {
        companion object {
            const val AUTHORITY = "fake.tree"
            // Document id -> children (name, is folder). Ids are "volume:path", as on external storage.
            private val TREE = mapOf(
                "primary:" to listOf("Music" to true, "Notifications" to true, "Recordings" to true,
                    "PodcastRepublic" to true, "Sounds" to true),
                "primary:Music" to listOf("song.mp3" to false, "Recordings" to true),
                "primary:Music/Recordings" to listOf("live.mp3" to false),
                "primary:Notifications" to listOf("ding.mp3" to false),
                "primary:Recordings" to listOf("Call" to true),
                "primary:Recordings/Call" to listOf("call.m4a" to false),
                "primary:PodcastRepublic" to listOf("MyMeditation" to true),
                "primary:PodcastRepublic/MyMeditation" to listOf("calm.mp3" to false),
                "primary:Sounds" to listOf("memo.m4a" to false)
            )
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val cursor = MatrixCursor(projection ?: emptyArray())
            val parent = DocumentsContract.getDocumentId(uri)
            for ((name, isFolder) in TREE[parent].orEmpty()) {
                val row = projection.orEmpty().map { column ->
                    when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID ->
                            if (parent.endsWith(':')) "$parent$name" else "$parent/$name"
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                        DocumentsContract.Document.COLUMN_MIME_TYPE ->
                            if (isFolder) DocumentsContract.Document.MIME_TYPE_DIR else "audio/mpeg"
                        DocumentsContract.Document.COLUMN_SIZE -> 1L
                        else -> null
                    }
                }
                cursor.addRow(row.toTypedArray())
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
