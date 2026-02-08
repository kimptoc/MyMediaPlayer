package com.example.mymediaplayer.shared

import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistServiceFallbackTest {

    @Test
    fun deleteFromTreeRoot_deletesWhenMatchFound() {
        val tempDir = Files.createTempDirectory("playlistRoot").toFile()
        val file = File(tempDir, "playlist.m3u")
        file.writeText("content")
        val root = DocumentFile.fromFile(tempDir)
        val service = PlaylistService()

        val result = service.deleteFromTreeRoot(root, "playlist.m3u")

        assertTrue(result)
        tempDir.deleteRecursively()
    }

    @Test
    fun deleteFromTreeRoot_returnsFalseWhenMissing() {
        val tempDir = Files.createTempDirectory("playlistRoot").toFile()
        val root = DocumentFile.fromFile(tempDir)
        val service = PlaylistService()

        val result = service.deleteFromTreeRoot(root, "playlist.m3u")

        assertFalse(result)
        tempDir.deleteRecursively()
    }
}
