package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(className = "androidx.documentfile.provider.SingleDocumentFile")
class ShadowSingleDocumentFileRenameSecurityException {
    @Implementation
    fun renameTo(displayName: String?): Boolean {
        throw SecurityException("Mock SecurityException")
    }
}

@Implements(className = "androidx.documentfile.provider.SingleDocumentFile")
class ShadowSingleDocumentFileRenameException {
    @Implementation
    fun renameTo(displayName: String?): Boolean {
        throw RuntimeException("Mock Exception")
    }
}

@Implements(className = "androidx.documentfile.provider.SingleDocumentFile")
class ShadowSingleDocumentFileRenameFalse {
    @Implementation
    fun renameTo(displayName: String?): Boolean {
        return false
    }
}

@Implements(className = "androidx.documentfile.provider.SingleDocumentFile")
class ShadowSingleDocumentFileRenameSuccess {
    @Implementation
    fun renameTo(displayName: String?): Boolean {
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
class PlaylistServiceRenameTest {

    @Test
    @Config(shadows = [ShadowSingleDocumentFileRenameSecurityException::class])
    fun renamePlaylist_catchesSecurityException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = "mock.documents.rename.sec"
        }
        Robolectric.buildContentProvider(MockDocumentProvider::class.java).create(providerInfo).get()
        val uri = DocumentsContract.buildDocumentUri("mock.documents.rename.sec", "123")

        val service = PlaylistService()
        val result = service.renamePlaylist(baseContext, uri, "new_name")
        assertNull(result)
    }

    @Test
    @Config(shadows = [ShadowSingleDocumentFileRenameException::class])
    fun renamePlaylist_catchesException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = "mock.documents.rename.exc"
        }
        Robolectric.buildContentProvider(MockDocumentProvider::class.java).create(providerInfo).get()
        val uri = DocumentsContract.buildDocumentUri("mock.documents.rename.exc", "123")

        val service = PlaylistService()
        val result = service.renamePlaylist(baseContext, uri, "new_name")
        assertNull(result)
    }

    @Test
    @Config(shadows = [ShadowSingleDocumentFileRenameFalse::class])
    fun renamePlaylist_returnsNullWhenRenameToReturnsFalse() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = "mock.documents.rename.false"
        }
        Robolectric.buildContentProvider(MockDocumentProvider::class.java).create(providerInfo).get()
        val uri = DocumentsContract.buildDocumentUri("mock.documents.rename.false", "123")

        val service = PlaylistService()
        val result = service.renamePlaylist(baseContext, uri, "new_name")
        assertNull(result)
    }

    @Test
    @Config(shadows = [ShadowSingleDocumentFileRenameSuccess::class])
    fun renamePlaylist_returnsPlaylistInfoWhenRenameToReturnsTrue() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = "mock.documents.rename.true"
        }
        Robolectric.buildContentProvider(MockDocumentProvider::class.java).create(providerInfo).get()
        val uri = DocumentsContract.buildDocumentUri("mock.documents.rename.true", "123")

        val service = PlaylistService()
        val result = service.renamePlaylist(baseContext, uri, "new_name")

        assertNotNull(result)
        assertEquals("new_name.m3u", result?.displayName)
    }
}
