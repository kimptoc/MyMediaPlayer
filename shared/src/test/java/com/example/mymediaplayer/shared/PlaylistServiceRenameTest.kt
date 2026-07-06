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

/**
 * Controls what the shadowed `renameTo` call does for the currently running test.
 * Robolectric instantiates shadow classes itself, so behavior is threaded through
 * via this companion rather than a constructor argument.
 */
sealed class RenameToBehavior {
    object ReturnFalse : RenameToBehavior()
    object ReturnTrue : RenameToBehavior()
    data class Throw(val exception: RuntimeException) : RenameToBehavior()
}

@Implements(className = "androidx.documentfile.provider.SingleDocumentFile")
class ShadowSingleDocumentFileRename {
    companion object {
        var behavior: RenameToBehavior = RenameToBehavior.ReturnTrue
    }

    @Implementation
    fun renameTo(displayName: String?): Boolean {
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

    private fun renamePlaylistWith(authority: String, behavior: RenameToBehavior): PlaylistInfo? {
        ShadowSingleDocumentFileRename.behavior = behavior
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            this.authority = authority
        }
        Robolectric.buildContentProvider(MockDocumentProvider::class.java).create(providerInfo).get()
        val uri = DocumentsContract.buildDocumentUri(authority, "123")

        return PlaylistService().renamePlaylist(baseContext, uri, "new_name")
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
    }
}
