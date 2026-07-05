package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.content.pm.ProviderInfo
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import androidx.documentfile.provider.DocumentFile

@Implements(className = "androidx.documentfile.provider.SingleDocumentFile")
class ShadowSingleDocumentFileException {
    @Implementation
    fun delete(): Boolean {
        throw SecurityException("Mocked SecurityException")
    }
}

@Implements(className = "androidx.documentfile.provider.TreeDocumentFile")
class ShadowTreeDocumentFileFallback {
    @Implementation
    fun findFile(displayName: String): DocumentFile? {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        return DocumentFile.fromSingleUri(context, Uri.parse("content://mock/playlist.m3u"))
    }
}

@Implements(className = "androidx.documentfile.provider.SingleDocumentFile")
class ShadowSingleDocumentFileSuccess {
    @Implementation
    fun delete(): Boolean {
        return true
    }
}

class MockDocumentProviderException : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw SecurityException("Mocked SecurityException")
    }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowSingleDocumentFileException::class])
class PlaylistServiceDeleteTest {

    @Test
    fun deletePlaylist_returnsFalseOnAllExceptions() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val providerInfo = ProviderInfo().apply {
            authority = "test"
        }
        Robolectric.buildContentProvider(MockDocumentProviderException::class.java).create(providerInfo).get()

        val uri = Uri.parse("content://test/playlist.m3u")
        val service = PlaylistService()

        val result = service.deletePlaylist(
            baseContext,
            uri
        )

        assertFalse(result)
    }

    @Test
    fun deletePlaylist_returnsFalseOnFallbackException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val providerInfo = ProviderInfo().apply {
            authority = "test"
        }
        Robolectric.buildContentProvider(MockDocumentProviderException::class.java).create(providerInfo)

        val uri = Uri.parse("content://test/playlist.m3u")
        val treeUri = Uri.parse("content://test/tree")
        val service = PlaylistService()

        val result = service.deletePlaylist(
            baseContext,
            uri,
            "test_playlist",
            treeUri
        )

        assertFalse(result)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowTreeDocumentFileFallback::class, ShadowSingleDocumentFileSuccess::class])
class PlaylistServiceDeleteFallbackTest {

    @Test
    fun deletePlaylist_returnsTrueOnFallbackSuccess() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val providerInfo = ProviderInfo().apply {
            authority = "test"
        }
        Robolectric.buildContentProvider(MockDocumentProviderException::class.java).create(providerInfo)

        val uri = Uri.parse("content://test/playlist.m3u")
        val treeUri = Uri.parse("content://mock/tree")
        val service = PlaylistService()

        val result = service.deletePlaylist(
            baseContext,
            uri,
            "test_playlist",
            treeUri
        )

        assertTrue(result)
    }
}
