package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class MediaCacheService {

    companion object {
        const val MAX_CACHE_SIZE = 20
        const val MAX_PLAYLIST_CACHE_SIZE = 20
        private const val UNKNOWN_DECADE = "Unknown Decade"
    }

    private val _cachedFiles = mutableListOf<MediaFileInfo>()
    val cachedFiles: List<MediaFileInfo>
        get() = synchronized(cacheLock) { _cachedFiles.toList() }

    private val _discoveredPlaylists = mutableListOf<PlaylistInfo>()
    val discoveredPlaylists: List<PlaylistInfo>
        get() = synchronized(cacheLock) { _discoveredPlaylists.toList() }

    private val cacheLock = Any()

    private val albumIndex = mutableMapOf<String, MutableList<MediaFileInfo>>()
    private val genreIndex = mutableMapOf<String, MutableList<MediaFileInfo>>()
    private val artistIndex = mutableMapOf<String, MutableList<MediaFileInfo>>()
    private val decadeIndex = mutableMapOf<String, MutableList<MediaFileInfo>>()
    private var metadataIndexed: Boolean = false
    private var albumArtistIndexed: Boolean = false
    private var maxFileLimit: Int = MAX_CACHE_SIZE
    private var foldersScanned: Int = 0
    private var songsFound: Int = 0

    fun scanDirectory(
        context: Context,
        treeUri: Uri,
        maxFiles: Int = MAX_CACHE_SIZE,
        onProgress: ((songsFound: Int, foldersScanned: Int) -> Unit)? = null
    ): ScanStats {
        clearCache()
        maxFileLimit = maxFiles.coerceAtLeast(1)
        foldersScanned = 0
        songsFound = 0
        val startTime = android.os.SystemClock.elapsedRealtime()
        var lastReported = 0
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        walkTree(context, treeUri, rootDocumentId) {
            if (onProgress != null && songsFound - lastReported >= 100) {
                lastReported = songsFound
                onProgress(songsFound, foldersScanned)
            }
        }
        val durationMs = android.os.SystemClock.elapsedRealtime() - startTime
        return ScanStats(
            durationMs = durationMs,
            foldersScanned = foldersScanned,
            songsFound = songsFound
        )
    }

    fun addFile(fileInfo: MediaFileInfo) {
        synchronized(cacheLock) {
            if (_cachedFiles.size < MAX_CACHE_SIZE) {
                _cachedFiles.add(fileInfo)
                metadataIndexed = false
            }
        }
    }

    fun addPlaylist(playlistInfo: PlaylistInfo) {
        synchronized(cacheLock) {
            _discoveredPlaylists.add(playlistInfo)
        }
    }

    private fun walkTree(
        context: Context,
        treeUri: Uri,
        rootDocumentId: String,
        onProgress: (() -> Unit)? = null
    ) {
        val contentResolver = context.contentResolver
        val toVisit = ArrayDeque<String>()
        toVisit.add(rootDocumentId)
        foldersScanned = 1

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
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
                val modifiedIndex =
                    it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (it.moveToNext()) {
                    if (isSearchComplete()) return

                    val childId = it.getString(idIndex)
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val mimeType = it.getString(mimeIndex)
                    val size = if (it.isNull(sizeIndex)) 0L else it.getLong(sizeIndex)
                    val lastModified =
                        if (it.isNull(modifiedIndex)) null else it.getLong(modifiedIndex)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        toVisit.add(childId)
                        foldersScanned += 1
                        continue
                    }

                    val lowerName = name.lowercase(Locale.US)
                    val shouldLoadFile = (lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a")) &&
                        synchronized(cacheLock) { _cachedFiles.size < maxFileLimit }
                    if (shouldLoadFile) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val metadata = MediaMetadataHelper.extractMetadata(context, uri.toString())
                        val fallbackYear = lastModified?.let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .year
                        }
                        val yearValue = metadata?.year?.toIntOrNull() ?: fallbackYear
                        val durationMs = metadata?.durationMs?.toLongOrNull()
                        synchronized(cacheLock) {
                            if (_cachedFiles.size < maxFileLimit) {
                        _cachedFiles.add(
                            MediaFileInfo(
                                uriString = uri.toString(),
                                displayName = name,
                                sizeBytes = size,
                                title = metadata?.title ?: name,
                                artist = metadata?.artist,
                                album = metadata?.album,
                                genre = metadata?.genre,
                                durationMs = durationMs,
                                year = yearValue
                            )
                        )
                                metadataIndexed = false
                                songsFound += 1
                                onProgress?.invoke()
                            }
                        }
                    } else if (lowerName.endsWith(".m3u") &&
                        synchronized(cacheLock) { _discoveredPlaylists.size < MAX_PLAYLIST_CACHE_SIZE }
                    ) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        synchronized(cacheLock) {
                            if (_discoveredPlaylists.size < MAX_PLAYLIST_CACHE_SIZE) {
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
        }
    }

    private fun isSearchComplete(): Boolean {
        synchronized(cacheLock) {
            return _cachedFiles.size >= maxFileLimit
        }
    }

    fun clearFiles() {
        synchronized(cacheLock) {
            _cachedFiles.clear()
            clearMetadataIndexes()
        }
    }

    fun clearPlaylists() {
        synchronized(cacheLock) {
            _discoveredPlaylists.clear()
        }
    }

    fun clearCache() {
        synchronized(cacheLock) {
            _cachedFiles.clear()
            _discoveredPlaylists.clear()
            clearMetadataIndexes()
        }
    }

    fun buildMetadataIndexes(context: Context) {
        clearMetadataIndexes()
        val snapshot = synchronized(cacheLock) { _cachedFiles.toList() }
        for (file in snapshot) {
            val metadata = MediaMetadataHelper.extractMetadata(context, file.uriString)
            val album = metadata?.album?.ifBlank { null } ?: "Unknown Album"
            val artist = metadata?.artist?.ifBlank { null } ?: "Unknown Artist"
            val genre = metadata?.genre?.ifBlank { null } ?: "Unknown Genre"
            val decade = decadeLabel(metadata?.year?.toIntOrNull() ?: file.year)

            albumIndex.getOrPut(album) { mutableListOf() }.add(file)
            artistIndex.getOrPut(artist) { mutableListOf() }.add(file)
            genreIndex.getOrPut(genre) { mutableListOf() }.add(file)
            decadeIndex.getOrPut(decade) { mutableListOf() }.add(file)
        }
        metadataIndexed = true
        albumArtistIndexed = true
    }

    fun hasMetadataIndexes(): Boolean = metadataIndexed

    fun hasAlbumArtistIndexes(): Boolean = albumArtistIndexed

    fun buildAlbumArtistIndexesFromCache() {
        clearMetadataIndexes()
        val snapshot = synchronized(cacheLock) { _cachedFiles.toList() }
        for (file in snapshot) {
            val album = file.album?.ifBlank { null } ?: "Unknown Album"
            val artist = file.artist?.ifBlank { null } ?: "Unknown Artist"
            val genre = file.genre?.ifBlank { null } ?: "Unknown Genre"
            val decade = decadeLabel(file.year)
            albumIndex.getOrPut(album) { mutableListOf() }.add(file)
            artistIndex.getOrPut(artist) { mutableListOf() }.add(file)
            genreIndex.getOrPut(genre) { mutableListOf() }.add(file)
            decadeIndex.getOrPut(decade) { mutableListOf() }.add(file)
        }
        albumArtistIndexed = true
    }

    fun albums(): List<String> = albumIndex.keys.sorted()

    fun genres(): List<String> = genreIndex.keys.sorted()

    fun artists(): List<String> = artistIndex.keys.sorted()

    fun decades(): List<String> {
        val labels = decadeIndex.keys.toMutableList()
        labels.remove(UNKNOWN_DECADE)
        labels.sortBy { it.removeSuffix("s").toIntOrNull() ?: Int.MAX_VALUE }
        if (decadeIndex.containsKey(UNKNOWN_DECADE)) {
            labels.add(UNKNOWN_DECADE)
        }
        return labels
    }

    fun songsForAlbum(album: String): List<MediaFileInfo> =
        albumIndex[album]?.toList() ?: emptyList()

    fun songsForGenre(genre: String): List<MediaFileInfo> =
        genreIndex[genre]?.toList() ?: emptyList()

    fun songsForArtist(artist: String): List<MediaFileInfo> =
        artistIndex[artist]?.toList() ?: emptyList()

    fun songsForDecade(decade: String): List<MediaFileInfo> =
        decadeIndex[decade]?.toList() ?: emptyList()

    private fun clearMetadataIndexes() {
        albumIndex.clear()
        genreIndex.clear()
        artistIndex.clear()
        decadeIndex.clear()
        metadataIndexed = false
        albumArtistIndexed = false
    }

    private fun decadeLabel(year: Int?): String {
        if (year == null || year <= 0) return UNKNOWN_DECADE
        val decade = (year / 10) * 10
        return "${decade}s"
    }

}

data class ScanStats(
    val durationMs: Long,
    val foldersScanned: Int,
    val songsFound: Int
)
