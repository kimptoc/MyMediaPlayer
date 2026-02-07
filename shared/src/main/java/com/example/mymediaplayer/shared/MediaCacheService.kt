package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class MediaCacheService {

    companion object {
        const val MAX_CACHE_SIZE = 20
    }

    private val _cachedFiles = mutableListOf<MediaFileInfo>()
    val cachedFiles: List<MediaFileInfo> get() = _cachedFiles.toList()

    fun scanDirectory(context: Context, treeUri: Uri) {
        clearCache()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        walkTree(root)
    }

    fun addFile(fileInfo: MediaFileInfo) {
        if (_cachedFiles.size < MAX_CACHE_SIZE) {
            _cachedFiles.add(fileInfo)
        }
    }

    private fun walkTree(directory: DocumentFile) {
        if (_cachedFiles.size >= MAX_CACHE_SIZE) return
        for (file in directory.listFiles()) {
            if (_cachedFiles.size >= MAX_CACHE_SIZE) return
            if (file.isDirectory) {
                walkTree(file)
            } else if (file.isFile && file.name?.endsWith(".mp3", ignoreCase = true) == true) {
                _cachedFiles.add(
                    MediaFileInfo(
                        uriString = file.uri.toString(),
                        displayName = file.name ?: "Unknown",
                        sizeBytes = file.length()
                    )
                )
            }
        }
    }

    fun clearCache() {
        _cachedFiles.clear()
    }
}
