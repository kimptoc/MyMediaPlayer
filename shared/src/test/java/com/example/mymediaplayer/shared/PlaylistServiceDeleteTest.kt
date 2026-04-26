package com.example.mymediaplayer.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

class MockDocumentProvider : ContentProvider() {
    var deleteCalled = false
    var failDelete = false

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "android:deleteDocument" || method == "deleteDocument") {
            deleteCalled = true
            if (failDelete) {
                throw SecurityException("Mock security exception")
            }
            return Bundle()
        }
        return super.call(method, arg, extras)
    }
}

@RunWith(RobolectricTestRunner::class)
class PlaylistServiceDeleteTest {

    @Test
    fun deletePlaylist_withDocumentsContract_returnsTrue() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = "mock.documents"
        }
        val mockProvider = Robolectric.buildContentProvider(MockDocumentProvider::class.java).create(providerInfo).get()
        val uri = DocumentsContract.buildDocumentUri("mock.documents", "123")

        val service = PlaylistService()
        val result = service.deletePlaylist(baseContext, uri)

        assertTrue(result)
        assertTrue(mockProvider.deleteCalled)
    }

    @Test
    fun deletePlaylist_withDocumentsContractFailing_returnsFalse() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = "mock.documents.fail"
        }
        val mockProvider = Robolectric.buildContentProvider(MockDocumentProvider::class.java).create(providerInfo).get()
        mockProvider.failDelete = true

        val uri = DocumentsContract.buildDocumentUri("mock.documents.fail", "123")
        val service = PlaylistService()
        val result = service.deletePlaylist(baseContext, uri)

        assertFalse(result)
        assertTrue(mockProvider.deleteCalled)
    }

    @Test
    fun deletePlaylist_withException_returnsFalse() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/playlist.m3u")
        // Just general failure where none of the strategies succeed.
        val service = PlaylistService()
        val result = service.deletePlaylist(baseContext, uri)
        assertFalse(result)
    }
}
