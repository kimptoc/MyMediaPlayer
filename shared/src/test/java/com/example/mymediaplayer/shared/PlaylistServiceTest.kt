package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class PlaylistServiceTest {

    @Test
    fun readPlaylist_returnsEmptyOnSecurityException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/playlist.m3u")
        val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
        shadowResolver.registerInputStreamSupplier(uri) {
            throw SecurityException("nope")
        }
        val service = PlaylistService()

        val results = service.readPlaylist(baseContext, uri)

        assertTrue(results.isEmpty())
    }

    @Test
    fun appendToPlaylist_returnsFalseOnIOException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/playlist.m3u")
        val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
        shadowResolver.registerOutputStreamSupplier(uri) {
            throw IOException("fail")
        }
        val service = PlaylistService()
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "Song One"
            )
        )

        val result = service.appendToPlaylist(
            baseContext,
            uri,
            files
        )

        assertFalse(result)
    }

    @Test
    fun renamePlaylist_returnsNullOnBlankName() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/playlist.m3u")
        val service = PlaylistService()

        val result = service.renamePlaylist(baseContext, uri, "   ")

        assertNull(result)
    }

    @Test
    fun writePlaylistWithName_returnsNullOnEmptyFiles() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = Uri.parse("content://test/tree")
        val service = PlaylistService()
        val files = emptyList<MediaFileInfo>()

        val result = service.writePlaylistWithName(baseContext, treeUri, files, "test_playlist")

        assertNull(result)
    }

    @Test
    fun writePlaylistWithName_returnsNullOnBlankName() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = Uri.parse("content://test/tree")
        val service = PlaylistService()
        val files = listOf(MediaFileInfo("content://test/song1", "Song One", 1L, "Song One"))

        val result = service.writePlaylistWithName(baseContext, treeUri, files, "   ")

        assertNull(result)
    }

    @Test
    fun generateM3uContent_stripsNewlines() {
        val service = PlaylistService()
        val files = listOf(
            MediaFileInfo(
                "content://test/song1\n#EXTINF:-1,Malicious\ncontent://test/malicious",
                "Song\r\nOne",
                1L,
                "Song One"
            )
        )

        val content = service.generateM3uContent(files)

        assertFalse(content.contains("Song\r\nOne"))
        assertTrue(content.contains("SongOne"))
        assertFalse(content.contains("song1\n"))
        assertTrue(content.contains("song1#EXTINF:-1,Maliciouscontent://test/malicious"))
    }

    @Test
    fun overwritePlaylist_returnsFalseOnEmptyFiles() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/playlist.m3u")
        val service = PlaylistService()
        val files = emptyList<MediaFileInfo>()

        val result = service.overwritePlaylist(
            baseContext,
            uri,
            files
        )

        assertFalse(result)
    }

    @Test
    fun overwritePlaylist_returnsFalseOnIOException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/playlist.m3u")
        val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
        shadowResolver.registerOutputStreamSupplier(uri) {
            throw IOException("fail")
        }
        val service = PlaylistService()
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "Song One"
            )
        )

        val result = service.overwritePlaylist(
            baseContext,
            uri,
            files
        )

        assertFalse(result)
    }

    @Test
    fun overwritePlaylist_returnsFalseOnSecurityException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/playlist.m3u")
        val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
        shadowResolver.registerOutputStreamSupplier(uri) {
            throw SecurityException("fail")
        }
        val service = PlaylistService()
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "Song One"
            )
        )

        val result = service.overwritePlaylist(
            baseContext,
            uri,
            files
        )

        assertFalse(result)
    }

    @org.robolectric.annotation.Config(shadows = [ShadowDocumentFile::class])
    @Test
    fun writePlaylistWithName_returnsNullOnSecurityException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = Uri.parse("content://mock/tree")
        val service = PlaylistService()
        val files = listOf(MediaFileInfo("content://test/song1", "Song One", 1L, "Song One"))

        val mockRoot = androidx.documentfile.provider.MockDocumentFile(null, treeUri, "tree")
        ShadowDocumentFile.mockRoot = mockRoot

        try {
            val targetUri = Uri.parse("$treeUri/test_playlist.m3u")
            val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
            shadowResolver.registerOutputStreamSupplier(targetUri) {
                object : java.io.OutputStream() {
                    override fun write(b: Int) {
                        throw SecurityException("Security exception thrown from write")
                    }
                    override fun write(b: ByteArray) {
                        throw SecurityException("Security exception thrown from write array")
                    }
                }
            }

            val result = service.writePlaylistWithName(baseContext, treeUri, files, "test_playlist")
            assertNull(result)
        } finally {
            ShadowDocumentFile.mockRoot = null
        }
    }

    @org.robolectric.annotation.Config(shadows = [ShadowDocumentFile::class])
    @Test
    fun writePlaylistWithName_returnsNullOnIOException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = Uri.parse("content://mock/tree")
        val service = PlaylistService()
        val files = listOf(MediaFileInfo("content://test/song1", "Song One", 1L, "Song One"))

        val mockRoot = androidx.documentfile.provider.MockDocumentFile(null, treeUri, "tree")
        ShadowDocumentFile.mockRoot = mockRoot

        try {
            val targetUri = Uri.parse("$treeUri/test_playlist.m3u")
            val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
            shadowResolver.registerOutputStreamSupplier(targetUri) {
                object : java.io.OutputStream() {
                    override fun write(b: Int) {
                        throw IOException("IO exception thrown from write")
                    }
                    override fun write(b: ByteArray) {
                        throw IOException("IO exception thrown from write array")
                    }
                }
            }

            val result = service.writePlaylistWithName(baseContext, treeUri, files, "test_playlist")
            assertNull(result)
        } finally {
            ShadowDocumentFile.mockRoot = null
        }
    }

    @Test
    fun overwritePlaylist_returnsTrueOnSuccess() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/playlist.m3u")
        val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
        var streamClosed = false
        shadowResolver.registerOutputStreamSupplier(uri) {
            object : java.io.ByteArrayOutputStream() {
                override fun close() {
                    super.close()
                    streamClosed = true
                }
            }
        }
        val service = PlaylistService()
        val files = listOf(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "Song One"
            )
        )

        val result = service.overwritePlaylist(
            baseContext,
            uri,
            files
        )

        assertTrue(result)
        assertTrue(streamClosed)
    }

}
