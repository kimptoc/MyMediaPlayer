package com.example.mymediaplayer.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.IOException

class NullInsertProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null // This will cause createFile to return null
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

class ValidInsertProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return Uri.parse("content://myauth_valid/document/new_file")
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

@RunWith(RobolectricTestRunner::class)
class PlaylistServiceErrorTest {

    @Test
    fun writePlaylistWithName_returnsNullWhenCreateFileFails() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val info = ProviderInfo().apply { authority = "myauth_null" }
        Robolectric.buildContentProvider(NullInsertProvider::class.java).create(info)

        val treeUri = Uri.parse("content://myauth_null/tree/doc")
        val service = PlaylistService()
        val files = listOf(MediaFileInfo("content://test/song1", "Song One", 1L, "Song One"))

        val result = service.writePlaylistWithName(baseContext, treeUri, files, "test_playlist")
        assertNull(result)
    }

    @Test
    fun writePlaylistWithName_returnsNullOnSecurityException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val info = ProviderInfo().apply { authority = "myauth_valid" }
        Robolectric.buildContentProvider(ValidInsertProvider::class.java).create(info)

        val treeUri = Uri.parse("content://myauth_valid/tree/doc")

        val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
        val targetUri = Uri.parse("content://myauth_valid/document/new_file")
        shadowResolver.registerOutputStreamSupplier(targetUri) {
            throw SecurityException("Mocked exception")
        }

        val service = PlaylistService()
        val files = listOf(MediaFileInfo("content://test/song1", "Song One", 1L, "Song One"))

        val result = service.writePlaylistWithName(baseContext, treeUri, files, "test_playlist")
        assertNull(result)
    }

    @Test
    fun writePlaylistWithName_returnsNullOnIOException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val info = ProviderInfo().apply { authority = "myauth_valid" }
        Robolectric.buildContentProvider(ValidInsertProvider::class.java).create(info)

        val treeUri = Uri.parse("content://myauth_valid/tree/doc")

        // Mock the output stream to throw IOException when the service tries to open the new file's URI
        val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
        val targetUri = Uri.parse("content://myauth_valid/document/new_file")
        shadowResolver.registerOutputStreamSupplier(targetUri) {
            throw IOException("Mocked exception")
        }

        val service = PlaylistService()
        val files = listOf(MediaFileInfo("content://test/song1", "Song One", 1L, "Song One"))

        val result = service.writePlaylistWithName(baseContext, treeUri, files, "test_playlist")
        assertNull(result)
    }

    @Test
    fun overwritePlaylist_returnsFalseOnIOException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val playlistUri = Uri.parse("content://myauth_valid/document/existing_file")

        val shadowResolver = Shadows.shadowOf(baseContext.contentResolver)
        shadowResolver.registerOutputStreamSupplier(playlistUri) {
            throw IOException("Mocked exception")
        }

        val service = PlaylistService()
        val files = listOf(MediaFileInfo("content://test/song1", "Song One", 1L, "Song One"))

        val result = service.overwritePlaylist(baseContext, playlistUri, files)
        assertFalse(result)
    }

}
