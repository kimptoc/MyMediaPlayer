package com.example.mymediaplayer.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
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

private class RenameMockDocumentProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

sealed class RenameToBehavior {
    object ReturnFalse : RenameToBehavior()
    object ReturnTrue : RenameToBehavior()
    data class Throw(val exception: RuntimeException) : RenameToBehavior()
}

@Implements(className = "androidx.documentfile.provider.SingleDocumentFile")
class ShadowSingleDocumentFileRename {
    companion object {
        var behavior: RenameToBehavior = RenameToBehavior.ReturnTrue
        var lastRequestedName: String? = null
    }

    @Implementation
    fun renameTo(displayName: String?): Boolean {
        lastRequestedName = displayName
        return when (val current = behavior) {
            is RenameToBehavior.ReturnFalse -> false
            is RenameToBehavior.ReturnTrue -> true
            is RenameToBehavior.Throw -> throw current.exception
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowSingleDocumentFileRename::class])
class PlaylistServiceRenameTest {

    private fun renamePlaylistWith(authority: String, behavior: RenameToBehavior, newName: String = "new_name"): PlaylistInfo? {
        ShadowSingleDocumentFileRename.behavior = behavior
        ShadowSingleDocumentFileRename.lastRequestedName = null
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            this.authority = authority
        }
        Robolectric.buildContentProvider(RenameMockDocumentProvider::class.java).create(providerInfo).get()
        val uri = DocumentsContract.buildDocumentUri(authority, "123")

        return PlaylistService().renamePlaylist(baseContext, uri, newName)
    }

    @Test
    fun renamePlaylist_catchesSecurityException() {
        val result = renamePlaylistWith(
            "mock.documents.rename.sec",
            RenameToBehavior.Throw(SecurityException("Mock SecurityException"))
        )
        assertNull(result)
    }

    @Test
    fun renamePlaylist_catchesRuntimeException() {
        val result = renamePlaylistWith(
            "mock.documents.rename.exc",
            RenameToBehavior.Throw(RuntimeException("Mock Exception"))
        )
        assertNull(result)
    }

    @Test
    fun renamePlaylist_returnsNullWhenRenameToReturnsFalse() {
        val result = renamePlaylistWith("mock.documents.rename.false", RenameToBehavior.ReturnFalse)
        assertNull(result)
    }

    @Test
    fun renamePlaylist_returnsPlaylistInfoWhenRenameToReturnsTrue() {
        val result = renamePlaylistWith("mock.documents.rename.true", RenameToBehavior.ReturnTrue)

        assertNotNull(result)
        assertEquals("new_name.m3u", result?.displayName)
        assertEquals("new_name.m3u", ShadowSingleDocumentFileRename.lastRequestedName)
    }

    @Test
    fun renamePlaylist_sanitizesPathTraversal() {
        val result = renamePlaylistWith("mock.documents.rename.traverse", RenameToBehavior.ReturnTrue, "../../../etc/passwd")
        assertNotNull(result)
        assertEquals("_________etc_passwd.m3u", result?.displayName)
        assertEquals("_________etc_passwd.m3u", ShadowSingleDocumentFileRename.lastRequestedName)
    }
}
