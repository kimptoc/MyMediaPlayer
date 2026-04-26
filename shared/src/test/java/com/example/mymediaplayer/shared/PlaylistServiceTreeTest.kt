package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File

@Implements(DocumentFile::class)
class ShadowDocumentFile {
    companion object {
        var mockRoot: DocumentFile? = null

        @Implementation
        @JvmStatic
        fun fromTreeUri(context: Context, treeUri: Uri): DocumentFile? {
            return mockRoot
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowDocumentFile::class])
class PlaylistServiceTreeTest {

    private lateinit var rootDir: File

    @Before
    fun setup() {
        rootDir = File(System.getProperty("java.io.tmpdir"), "test_tree_root_${System.currentTimeMillis()}")
        rootDir.mkdirs()
    }

    @After
    fun teardown() {
        ShadowDocumentFile.mockRoot = null
        if (::rootDir.isInitialized) {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun testListPlaylistsInTree() {
        runBlocking {
            val baseContext = ApplicationProvider.getApplicationContext<Context>()

            val f1 = File(rootDir, "playlist.m3u").apply { createNewFile() }
            val f2 = File(rootDir, "ignore.txt").apply { createNewFile() }

            val nestedDir = File(rootDir, "nested").apply { mkdirs() }
            val f3 = File(nestedDir, "another.pls").apply { createNewFile() }
            val f4 = File(nestedDir, "MIX.M3U8").apply { createNewFile() }

            val rootDoc = DocumentFile.fromFile(rootDir)
            ShadowDocumentFile.mockRoot = rootDoc

            val service = PlaylistService()
            val results = service.listPlaylistsInTree(baseContext, Uri.parse("content://mock/tree"))

            assertEquals(3, results.size)
            assertEquals("another.pls", results[0].displayName)
            assertEquals("MIX.M3U8", results[1].displayName)
            assertEquals("playlist.m3u", results[2].displayName)

            assertEquals(Uri.fromFile(f3).toString(), results[0].uriString)
            assertEquals(Uri.fromFile(f4).toString(), results[1].uriString)
            assertEquals(Uri.fromFile(f1).toString(), results[2].uriString)
        }
    }

    @Test
    fun testListPlaylistsInTree_empty() {
        runBlocking {
            val baseContext = ApplicationProvider.getApplicationContext<Context>()
            ShadowDocumentFile.mockRoot = null

            val service = PlaylistService()
            val results = service.listPlaylistsInTree(baseContext, Uri.parse("content://mock/invalid"))

            assertEquals(0, results.size)
        }
    }
}
