package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class PersistedCache(
    val files: List<MediaFileInfo>,
    val playlists: List<PlaylistInfo>,
    val scannedAt: Long
)

class MediaCacheService {

    companion object {
        const val MAX_CACHE_SIZE = 20
        const val MAX_PLAYLIST_CACHE_SIZE = 20
        private const val UNKNOWN_DECADE = "Unknown Decade"
        private const val METADATA_BATCH_SIZE = 50
        private const val METADATA_PARALLELISM = 4
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
    private var albumArtistIndexed: Boolean = false
    private var maxFileLimit: Int = MAX_CACHE_SIZE
    private var foldersScanned: Int = 0
    private var songsFound: Int = 0

    suspend fun scanDirectory(
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
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

        // Phase 1: Walk tree to collect file candidates (fast - content provider queries only)
        val candidates = mutableListOf<FileCandidate>()
        val parentContext = coroutineContext
        val shouldContinue: () -> Boolean = { parentContext.isActive }
        walkTree(context, treeUri, rootDocumentId, candidates, shouldContinue)
        onProgress?.invoke(candidates.size, foldersScanned)

        // Phase 2: Extract metadata in parallel batches
        val metadataDispatcher = Dispatchers.IO.limitedParallelism(METADATA_PARALLELISM)
        var processed = 0
        for (batch in candidates.chunked(METADATA_BATCH_SIZE)) {
            val results = coroutineScope {
                batch.map { candidate ->
                    async(metadataDispatcher) {
                        val metadata = MediaMetadataHelper.extractMetadata(context, candidate.uri.toString())
                        val fallbackYear = candidate.lastModified?.let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .year
                        }
                        val yearValue = metadata?.year?.toIntOrNull() ?: fallbackYear
                        val durationMs = metadata?.durationMs?.toLongOrNull()
                        MediaFileInfo(
                            uriString = candidate.uri.toString(),
                            displayName = candidate.name,
                            sizeBytes = candidate.size,
                            title = metadata?.title ?: candidate.name,
                            artist = metadata?.artist,
                            album = metadata?.album,
                            genre = normalizeGenre(metadata?.genre),
                            durationMs = durationMs,
                            year = yearValue
                        )
                    }
                }.awaitAll()
            }
            synchronized(cacheLock) {
                _cachedFiles.addAll(results)
                albumArtistIndexed = false
            }
            for (result in results) {
                processed += 1
                songsFound = processed
                onProgress?.invoke(songsFound, foldersScanned)
            }
        }

        val durationMs = android.os.SystemClock.elapsedRealtime() - startTime
        return ScanStats(
            durationMs = durationMs,
            foldersScanned = foldersScanned,
            songsFound = songsFound
        )
    }

    fun loadPersistedCache(
        context: Context,
        treeUri: Uri,
        maxFiles: Int
    ): PersistedCache? {
        val db = MediaCacheDatabase.getInstance(context)
        val dao = db.cacheDao()
        val state = dao.getScanState() ?: return null
        if (state.treeUri != treeUri.toString() || state.scanLimit != maxFiles) {
            return null
        }
        val files = dao.getAllFiles().map {
            MediaFileInfo(
                uriString = it.uriString,
                displayName = it.displayName,
                sizeBytes = it.sizeBytes,
                title = it.title,
                artist = it.artist,
                album = it.album,
                genre = it.genre,
                durationMs = it.durationMs,
                year = it.year
            )
        }
        val playlists = dao.getAllPlaylists().map {
            PlaylistInfo(
                uriString = it.uriString,
                displayName = it.displayName
            )
        }
        synchronized(cacheLock) {
            _cachedFiles.clear()
            _cachedFiles.addAll(files)
            _discoveredPlaylists.clear()
            _discoveredPlaylists.addAll(playlists)
            albumArtistIndexed = false
        }
        return PersistedCache(files, playlists, state.scannedAt)
    }

    fun persistCache(context: Context, treeUri: Uri, maxFiles: Int) {
        val db = MediaCacheDatabase.getInstance(context)
        val dao = db.cacheDao()
        val files = synchronized(cacheLock) { _cachedFiles.toList() }.map {
            MediaFileEntity(
                uriString = it.uriString,
                displayName = it.displayName,
                sizeBytes = it.sizeBytes,
                title = it.title,
                artist = it.artist,
                album = it.album,
                genre = it.genre,
                durationMs = it.durationMs,
                year = it.year
            )
        }
        val playlists = synchronized(cacheLock) { _discoveredPlaylists.toList() }.map {
            PlaylistEntity(
                uriString = it.uriString,
                displayName = it.displayName
            )
        }
        val state = ScanStateEntity(
            treeUri = treeUri.toString(),
            scanLimit = maxFiles,
            scannedAt = System.currentTimeMillis()
        )
        dao.replaceCache(files, playlists, state)
    }

    fun addFile(fileInfo: MediaFileInfo) {
        synchronized(cacheLock) {
            if (_cachedFiles.size < MAX_CACHE_SIZE) {
                _cachedFiles.add(fileInfo)
                albumArtistIndexed = false
            }
        }
    }

    fun addPlaylist(playlistInfo: PlaylistInfo) {
        synchronized(cacheLock) {
            _discoveredPlaylists.add(playlistInfo)
        }
    }

    fun removePlaylistByUri(uriString: String) {
        synchronized(cacheLock) {
            _discoveredPlaylists.removeAll { it.uriString == uriString }
        }
    }

    private data class FileCandidate(
        val uri: Uri,
        val name: String,
        val size: Long,
        val lastModified: Long?
    )

    private fun walkTree(
        context: Context,
        treeUri: Uri,
        rootDocumentId: String,
        candidates: MutableList<FileCandidate>,
        shouldContinue: () -> Boolean
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
            if (!shouldContinue()) return
            if (candidates.size >= maxFileLimit) return

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
                if (!shouldContinue()) return
                if (candidates.size >= maxFileLimit) return

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
                    val isAudio = lowerName.endsWith(".mp3") ||
                        lowerName.endsWith(".m4a") ||
                        lowerName.endsWith(".aac") ||
                        lowerName.endsWith(".flac") ||
                        lowerName.endsWith(".ogg") ||
                        lowerName.endsWith(".opus") ||
                        lowerName.endsWith(".wav") ||
                        lowerName.endsWith(".m4b") ||
                        lowerName.endsWith(".aiff") ||
                        lowerName.endsWith(".aif")
                    if (isAudio) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        candidates.add(FileCandidate(uri, name, size, lastModified))
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

    fun hasAlbumArtistIndexes(): Boolean = albumArtistIndexed

    fun buildAlbumArtistIndexesFromCache() {
        clearMetadataIndexes()
        val snapshot = synchronized(cacheLock) { _cachedFiles.toList() }
        for (file in snapshot) {
            val album = file.album?.ifBlank { null } ?: "Unknown Album"
            val artist = file.artist?.ifBlank { null } ?: "Unknown Artist"
            val genre = normalizeGenre(file.genre)
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

    fun searchFiles(query: String): List<MediaFileInfo> {
        val needle = query.trim().lowercase(Locale.US)
        if (needle.isBlank()) return emptyList()
        val snapshot = synchronized(cacheLock) { _cachedFiles.toList() }
        return snapshot.filter { file ->
            val title = file.title ?: file.displayName
            val haystack = listOfNotNull(
                title,
                file.artist,
                file.album,
                file.genre
            ).joinToString(" ").lowercase(Locale.US)
            haystack.contains(needle)
        }
    }

    private fun clearMetadataIndexes() {
        albumIndex.clear()
        genreIndex.clear()
        artistIndex.clear()
        decadeIndex.clear()
        albumArtistIndexed = false
    }

    private fun normalizeGenre(raw: String?): String {
        val value = raw?.trim()?.lowercase(Locale.US).orEmpty()
        if (value.isBlank()) return "Other"

        fun hasAny(vararg tokens: String): Boolean = tokens.any { value.contains(it) }

        return when {
            hasAny("hip hop", "hip-hop", "rap", "trap") -> "Hip-Hop"
            hasAny("r&b", "rnb", "rhythm and blues") -> "R&B"
            hasAny("electronic", "edm", "electronica", "techno", "house", "trance", "dubstep") ->
                "Electronic"
            hasAny("rock", "alternative", "grunge", "punk", "indie rock", "hard rock") -> "Rock"
            hasAny("pop", "synthpop", "dance pop") -> "Pop"
            hasAny("jazz", "swing", "bebop") -> "Jazz"
            hasAny("classical", "orchestral", "symphony", "baroque", "opera") -> "Classical"
            hasAny("country", "bluegrass") -> "Country"
            hasAny("metal", "heavy metal", "death metal", "black metal") -> "Metal"
            hasAny("reggae", "ska", "dancehall") -> "Reggae"
            hasAny("latin", "salsa", "bachata", "reggaeton", "tango") -> "Latin"
            else -> "Other"
        }
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
