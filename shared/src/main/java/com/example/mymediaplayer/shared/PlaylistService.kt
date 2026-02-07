package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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

    fun writePlaylist(context: Context, treeUri: Uri, files: List<MediaFileInfo>): String? {
        if (files.isEmpty()) return null

        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
            .format(LocalDateTime.now())
        val fileName = "playlist_${timestamp}.m3u"
        val target = root.createFile("audio/x-mpegurl", fileName) ?: return null
        val content = generateM3uContent(files)

        context.contentResolver.openOutputStream(target.uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
        } ?: return null

        return fileName
    }
}
