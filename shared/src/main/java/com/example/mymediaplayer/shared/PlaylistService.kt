package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PlaylistService {

    fun generateM3uContent(files: List<MediaFileInfo>): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")
        for (file in files) {
            builder.append("#EXTINF:-1,")
            builder.append(file.displayName)
            builder.append("\n")
            builder.append(file.uriString)
            builder.append("\n")
        }
        return builder.toString()
    }

    fun writePlaylist(context: Context, treeUri: Uri, files: List<MediaFileInfo>): PlaylistInfo? {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
            .format(LocalDateTime.now())
        return writePlaylistWithName(context, treeUri, files, "playlist_$timestamp")
    }

    fun writePlaylistWithName(
        context: Context,
        treeUri: Uri,
        files: List<MediaFileInfo>,
        name: String
    ): PlaylistInfo? {
        if (files.isEmpty()) return null
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null

        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val safeName = trimmed.replace("/", "_")
        val fileName = if (safeName.endsWith(".m3u", ignoreCase = true)) {
            safeName
        } else {
            "$safeName.m3u"
        }
        val target = root.createFile("audio/x-mpegurl", fileName) ?: return null
        val content = generateM3uContent(files)

        context.contentResolver.openOutputStream(target.uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
        } ?: return null

        return PlaylistInfo(
            uriString = target.uri.toString(),
            displayName = fileName
        )
    }

    fun appendToPlaylist(
        context: Context,
        playlistUri: Uri,
        files: List<MediaFileInfo>
    ): Boolean {
        if (files.isEmpty()) return false
        val content = generateM3uContent(files).removePrefix("#EXTM3U\n")
        val output = context.contentResolver.openOutputStream(playlistUri, "wa") ?: return false
        output.use { stream ->
            stream.write("\n".toByteArray())
            stream.write(content.toByteArray())
            stream.flush()
        }
        return true
    }

    fun deletePlaylist(
        context: Context,
        playlistUri: Uri,
        displayName: String? = null,
        treeUri: Uri? = null
    ): Boolean {
        val doc = DocumentFile.fromSingleUri(context, playlistUri)
            ?: DocumentFile.fromTreeUri(context, playlistUri)

        if (doc != null && doc.delete()) return true

        try {
            if (DocumentsContract.deleteDocument(context.contentResolver, playlistUri)) return true
        } catch (_: Exception) {
        }

        if (treeUri != null && !displayName.isNullOrBlank()) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val match = root.findFile(displayName) ?: return false
            if (match.delete()) return true
        }

        return false
    }

    fun readPlaylist(context: Context, playlistUri: Uri): List<MediaFileInfo> {
        val results = mutableListOf<MediaFileInfo>()
        val inputStream = context.contentResolver.openInputStream(playlistUri) ?: return results
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var pendingTitle: String? = null
            reader.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach
                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    val commaIndex = trimmed.indexOf(',')
                    pendingTitle = if (commaIndex >= 0 && commaIndex + 1 < trimmed.length) {
                        trimmed.substring(commaIndex + 1).trim().ifEmpty { null }
                    } else {
                        null
                    }
                    return@forEach
                }
                if (trimmed.startsWith("#")) return@forEach

                val uri = Uri.parse(trimmed)
                val name = pendingTitle ?: uri.lastPathSegment ?: "Unknown"
                results.add(
                    MediaFileInfo(
                        uriString = trimmed,
                        displayName = name,
                        sizeBytes = 0L,
                        title = name
                    )
                )
                pendingTitle = null
            }
        }
        return results
    }
}
