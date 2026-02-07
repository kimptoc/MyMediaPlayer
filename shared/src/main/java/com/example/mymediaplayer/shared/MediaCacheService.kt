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

    private val _discoveredPlaylists = mutableListOf<PlaylistInfo>()
    val discoveredPlaylists: List<PlaylistInfo> get() = _discoveredPlaylists.toList()

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

    fun addPlaylist(playlistInfo: PlaylistInfo) {
        _discoveredPlaylists.add(playlistInfo)
    }

    private fun walkTree(directory: DocumentFile) {
        for (file in directory.listFiles()) {
            if (file.isDirectory) {
                walkTree(file)
            } else if (file.isFile) {
                val name = file.name ?: "Unknown"
                when {
                    name.endsWith(".mp3", ignoreCase = true) ||
                        name.endsWith(".m4a", ignoreCase = true) -> {
                        if (_cachedFiles.size < MAX_CACHE_SIZE) {
                            _cachedFiles.add(
                                MediaFileInfo(
                                    uriString = file.uri.toString(),
                                    displayName = name,
                                    sizeBytes = file.length()
                                )
                            )
                        }
                    }
                    name.endsWith(".m3u", ignoreCase = true) -> {
                        _discoveredPlaylists.add(
                            PlaylistInfo(
                                uriString = file.uri.toString(),
                                displayName = name
                            )
                        )
                    }
                }
            }
        }
    }

    fun clearFiles() {
        _cachedFiles.clear()
    }

    fun clearPlaylists() {
        _discoveredPlaylists.clear()
    }

    fun clearCache() {
        _cachedFiles.clear()
        _discoveredPlaylists.clear()
    }
}
