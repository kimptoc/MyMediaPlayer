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

}
