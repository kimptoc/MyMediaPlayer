package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistInfoTest {

    @Test
    fun playlistShortId_isDeterministic() {
        val uri = "content://com.example.provider/some/long/path/to/playlist"
        val id1 = playlistShortId(uri)
        val id2 = playlistShortId(uri)
        assertEquals(id1, id2)
    }

    @Test
    fun playlistShortId_withLongUri_returnsShortId() {
        val longUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FPlaylists/document/primary%3AMusic%2FPlaylists%2FMy%20Awesome%20Summer%20Playlist%202023.m3u"
        val id = playlistShortId(longUri)

        assertTrue("Expected short ID, got $id (length ${id.length})", id.length <= 10)
        assertNotEquals(longUri, id)
    }

    @Test
    fun playlistShortId_differentUris_produceDifferentIds() {
        val uri1 = "content://something/1"
        val uri2 = "content://something/2"

        assertNotEquals(playlistShortId(uri1), playlistShortId(uri2))
    }

    @Test
    fun playlistShortId_emptyString_returnsValidId() {
        val id = playlistShortId("")
        assertTrue(id.isNotEmpty())
    }
}
