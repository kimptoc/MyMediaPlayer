package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

class MediaCacheService {

    companion object {
        const val MAX_CACHE_SIZE = 20
        const val MAX_PLAYLIST_CACHE_SIZE = 20
    }

    private val _cachedFiles = mutableListOf<MediaFileInfo>()
    val cachedFiles: List<MediaFileInfo> get() = _cachedFiles.toList()

    private val _discoveredPlaylists = mutableListOf<PlaylistInfo>()
    val discoveredPlaylists: List<PlaylistInfo> get() = _discoveredPlaylists.toList()

    fun scanDirectory(context: Context, treeUri: Uri) {
        clearCache()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        walkTree(context, treeUri, rootDocumentId)
    }

    fun addFile(fileInfo: MediaFileInfo) {
        if (_cachedFiles.size < MAX_CACHE_SIZE) {
            _cachedFiles.add(fileInfo)
        }
    }

    fun addPlaylist(playlistInfo: PlaylistInfo) {
        _discoveredPlaylists.add(playlistInfo)
    }

    private fun walkTree(context: Context, treeUri: Uri, rootDocumentId: String) {
        val contentResolver = context.contentResolver
        val toVisit = ArrayDeque<String>()
        toVisit.add(rootDocumentId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        while (toVisit.isNotEmpty()) {
            if (isSearchComplete()) return

            val documentId = toVisit.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = contentResolver.query(childrenUri, projection, null, null, null) ?: continue

            cursor.use {
                val idIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (it.moveToNext()) {
                    if (isSearchComplete()) return

                    val childId = it.getString(idIndex)
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val mimeType = it.getString(mimeIndex)
                    val size = if (it.isNull(sizeIndex)) 0L else it.getLong(sizeIndex)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        toVisit.add(childId)
                        continue
                    }

                    val lowerName = name.lowercase(Locale.US)
                    if ((lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a")) &&
                        _cachedFiles.size < MAX_CACHE_SIZE
                    ) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        _cachedFiles.add(
                            MediaFileInfo(
                                uriString = uri.toString(),
                                displayName = name,
                                sizeBytes = size
                            )
                        )
                    } else if (lowerName.endsWith(".m3u") &&
                        _discoveredPlaylists.size < MAX_PLAYLIST_CACHE_SIZE
                    ) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        _discoveredPlaylists.add(
                            PlaylistInfo(
                                uriString = uri.toString(),
                                displayName = name
                            )
                        )
                    }
                }
            }
        }
    }

    private fun isSearchComplete(): Boolean {
        val filesFull = _cachedFiles.size >= MAX_CACHE_SIZE
        val playlistsFull = _discoveredPlaylists.size >= MAX_PLAYLIST_CACHE_SIZE
        return filesFull && playlistsFull
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
