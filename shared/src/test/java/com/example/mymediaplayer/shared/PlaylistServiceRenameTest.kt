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
@Config(sdk = [26])
class PlaylistServiceRenameTest {

    @Test
    @Config(shadows = [ShadowSingleDocumentFileRenameSecurityException::class])
    fun renamePlaylist_singleUri_catchesSecurityException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val uri = Uri.parse("content://mock.documents.rename.sec/document/123")

        val service = PlaylistService()
        val result = service.renamePlaylist(baseContext, uri, "new_name")
        assertNull(result)
    }

    @Test
    @Config(shadows = [ShadowSingleDocumentFileRenameException::class])
    fun renamePlaylist_singleUri_catchesException() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://mock.documents.rename.exc/document/123")

        val service = PlaylistService()
        val result = service.renamePlaylist(baseContext, uri, "new_name")
        assertNull(result)
    }

    @Test
    @Config(shadows = [ShadowSingleDocumentFileRenameFalse::class])
    fun renamePlaylist_singleUri_returnsNullWhenRenameToReturnsFalse() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://mock.documents.rename.false/document/123")

        val service = PlaylistService()
        val result = service.renamePlaylist(baseContext, uri, "new_name")
        assertNull(result)
    }

    @Test
    @Config(shadows = [ShadowSingleDocumentFileRenameSuccess::class])
    fun renamePlaylist_singleUri_returnsPlaylistInfoWhenRenameToReturnsTrue() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://mock.documents.rename.true/document/123")

        val service = PlaylistService()
        val result = service.renamePlaylist(baseContext, uri, "new_name")

        assertNotNull(result)
        assertEquals("new_name.m3u", result?.displayName)
    }
}
