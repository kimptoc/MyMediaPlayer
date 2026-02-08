package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PlaylistService {

    companion object {
        private const val TAG = "PlaylistService"
    }

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
        val target = root.createFile("audio/x-mpegurl", fileName) ?: run {
            Log.e(TAG, "Failed to create playlist file: $fileName")
            return null
        }
        val content = generateM3uContent(files)

        try {
            context.contentResolver.openOutputStream(target.uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                outputStream.flush()
            } ?: run {
                Log.e(TAG, "Could not open output stream for playlist: $fileName")
                return null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied writing playlist: $fileName", e)
            return null
        } catch (e: IOException) {
            Log.e(TAG, "I/O error writing playlist: $fileName", e)
            return null
        }

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
        try {
            val output = context.contentResolver.openOutputStream(playlistUri, "wa") ?: run {
                Log.e(TAG, "Could not open output stream for append: $playlistUri")
                return false
            }
            output.use { stream ->
                stream.write("\n".toByteArray())
                stream.write(content.toByteArray())
                stream.flush()
            }
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied appending to playlist: $playlistUri", e)
            return false
        } catch (e: IOException) {
            Log.e(TAG, "I/O error appending to playlist: $playlistUri", e)
            return false
        }
    }

    fun deletePlaylist(
        context: Context,
        playlistUri: Uri,
        displayName: String? = null,
        treeUri: Uri? = null
    ): Boolean {
        try {
            val doc = DocumentFile.fromSingleUri(context, playlistUri)
                ?: DocumentFile.fromTreeUri(context, playlistUri)

            if (doc != null && doc.delete()) return true
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied deleting via DocumentFile: $playlistUri", e)
        } catch (e: Exception) {
            Log.w(TAG, "DocumentFile delete failed: $playlistUri", e)
        }

        try {
            if (DocumentsContract.deleteDocument(context.contentResolver, playlistUri)) return true
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied deleting via DocumentsContract: $playlistUri", e)
        } catch (e: Exception) {
            Log.w(TAG, "DocumentsContract delete failed: $playlistUri", e)
        }

        if (treeUri != null && !displayName.isNullOrBlank()) {
            try {
                val root = DocumentFile.fromTreeUri(context, treeUri)
                if (deleteFromTreeRoot(root, displayName)) return true
            } catch (e: Exception) {
                Log.w(TAG, "Fallback delete failed for $displayName", e)
            }
        }

        Log.e(TAG, "All delete strategies failed for playlist: $playlistUri")
        return false
    }

    internal fun deleteFromTreeRoot(root: DocumentFile?, displayName: String?): Boolean {
        if (root == null || displayName.isNullOrBlank()) return false
        val match = root.findFile(displayName) ?: return false
        return match.delete()
    }

    fun readPlaylist(context: Context, playlistUri: Uri): List<MediaFileInfo> {
        val results = mutableListOf<MediaFileInfo>()
        val inputStream = try {
            context.contentResolver.openInputStream(playlistUri)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied reading playlist: $playlistUri", e)
            return results
        } catch (e: java.io.FileNotFoundException) {
            Log.e(TAG, "Playlist file not found: $playlistUri", e)
            return results
        } catch (e: IOException) {
            Log.e(TAG, "I/O error opening playlist: $playlistUri", e)
            return results
        }
        if (inputStream == null) {
            Log.w(TAG, "Could not open input stream for playlist: $playlistUri")
            return results
        }
        try {
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
        } catch (e: IOException) {
            Log.e(TAG, "I/O error reading playlist content: $playlistUri", e)
        }
        return results
    }
}
