package com.example.mymediaplayer.shared

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
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
        const val MAX_CACHE_SIZE = 50000
        const val MAX_PLAYLIST_CACHE_SIZE = 20
        private const val UNKNOWN_DECADE = "Unknown Decade"
        private const val METADATA_BATCH_SIZE = 50
        private const val METADATA_PARALLELISM = 4
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            ".mp3", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wav", ".m4b",
            ".aiff", ".aif", ".wma", ".alac", ".ape", ".amr", ".awb",
            ".3gp", ".3gpp", ".3g2", ".3gp2",
            ".mp2", ".mp1", ".mpa", ".mpga",
            ".ac3", ".eac3", ".dts",
            ".mid", ".midi", ".mka", ".weba", ".webm", ".tta", ".wv"
        )
    }

    suspend fun scanWholeDevice(
        context: Context,
        maxFiles: Int = MAX_CACHE_SIZE,
        onProgress: ((songsFound: Int, foldersScanned: Int) -> Unit)? = null
    ): ScanStats = kotlinx.coroutines.withContext(Dispatchers.IO) {
        clearCache()
        maxFileLimit = maxFiles.coerceAtLeast(1)
        foldersScanned = 0
        songsFound = 0
        val startTime = android.os.SystemClock.elapsedRealtime()
        val extensionCounts = mutableMapOf<String, Int>()
        val out = mutableListOf<MediaFileInfo>()
        val outIds = mutableListOf<Long>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext() && out.size < maxFileLimit) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val displayName = cursor.getString(nameIndex) ?: uri.toString()
                val sizeBytes = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                val title = cursor.getString(titleIndex)
                val artist = cursor.getString(artistIndex)
                val album = cursor.getString(albumIndex)
                val relativePath = if (relativePathIndex >= 0 && !cursor.isNull(relativePathIndex)) {
                    cursor.getString(relativePathIndex)
                } else {
                    null
                }
                val durationMs = if (cursor.isNull(durationIndex)) null else cursor.getLong(durationIndex)
                val year = if (cursor.isNull(yearIndex)) null else cursor.getInt(yearIndex)
                val addedAtMs = if (cursor.isNull(addedIndex)) {
                    null
                } else {
                    cursor.getLong(addedIndex) * 1000L
                }
                out.add(
                    MediaFileInfo(
                        uriString = uri.toString(),
                        displayName = displayName,
                        sizeBytes = sizeBytes,
                        title = title,
                        artist = artist,
                        album = album,
                        genre = normalizeGenre(inferGenreFromPath(relativePath)),
                        durationMs = durationMs,
                        year = year,
                        addedAtMs = addedAtMs
                    )
                )
                outIds.add(id)
                songsFound = out.size
                val ext = fileExtension(displayName)
                extensionCounts[ext] = (extensionCounts[ext] ?: 0) + 1
                if (songsFound % 200 == 0 || songsFound == 1) {
                    onProgress?.invoke(songsFound, 0)
                }
            }
        }
        val genresByAudioId = loadWholeDriveGenres(context, outIds.toSet())
        if (genresByAudioId.isNotEmpty()) {
            for (index in out.indices) {
                val audioId = outIds[index]
                val mappedGenre = genresByAudioId[audioId] ?: continue
                out[index] = out[index].copy(genre = normalizeGenre(mappedGenre))
            }
        }
        synchronized(cacheLock) {
            _cachedFiles.clear()
            _cachedFiles.addAll(out)
            _cachedFilesByUri.clear()
            _cachedFilesByUri.putAll(out.associateBy { it.uriString })
            _discoveredPlaylists.clear()
            albumArtistIndexed = false
        }
        onProgress?.invoke(out.size, 0)
        val durationMs = android.os.SystemClock.elapsedRealtime() - startTime
        ScanStats(
            durationMs = durationMs,
            foldersScanned = 0,
            songsFound = out.size,
            extensionCounts = extensionCounts.toMap(),
            skippedReasons = emptyMap(),
            deepScan = false,
            probedCandidates = 0
        )
    }

    private fun loadWholeDriveGenres(context: Context, audioIds: Set<Long>): Map<Long, String> {
        if (audioIds.isEmpty()) return emptyMap()
        val resolver = context.contentResolver
        val genresByAudioId = mutableMapOf<Long, String>()
        val genreProjection = arrayOf(
            MediaStore.Audio.Genres._ID,
            MediaStore.Audio.Genres.NAME
        )
        resolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            genreProjection,
            null,
            null,
            null
        )?.use { genreCursor ->
            val genreIdIndex = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val genreNameIndex = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (genreCursor.moveToNext()) {
                val genreId = genreCursor.getLong(genreIdIndex)
                val genreName = genreCursor.getString(genreNameIndex)?.trim().orEmpty()
                if (genreName.isBlank()) continue
                val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                resolver.query(
                    membersUri,
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null,
                    null,
                    null
                )?.use { membersCursor ->
                    val audioIdIndex =
                        membersCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    while (membersCursor.moveToNext()) {
                        val audioId = membersCursor.getLong(audioIdIndex)
                        if (audioId !in audioIds) continue
                        if (genresByAudioId[audioId].isNullOrBlank()) {
                            genresByAudioId[audioId] = genreName
                        }
                    }
                }
            }
        }
        return genresByAudioId
    }

    fun enrichGenresFromMediaStore(context: Context) {
        val resolver = context.contentResolver
        // Build audioId → displayName map from MediaStore
        val audioIdByName = mutableMapOf<String, Long>()
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                audioIdByName[name] = cursor.getLong(idIdx)
            }
        }
        if (audioIdByName.isEmpty()) return

        val genresByAudioId = loadWholeDriveGenres(context, audioIdByName.values.toSet())
        if (genresByAudioId.isEmpty()) return

        synchronized(cacheLock) {
            for (i in _cachedFiles.indices) {
                val file = _cachedFiles[i]
                val audioId = audioIdByName[file.displayName] ?: continue
                val genre = genresByAudioId[audioId] ?: continue
                _cachedFiles[i] = file.copy(genre = normalizeGenre(genre))
                _cachedFilesByUri[file.uriString] = _cachedFiles[i]
            }
        }
    }

    private val _cachedFiles = mutableListOf<MediaFileInfo>()
    val cachedFiles: List<MediaFileInfo>
        get() = synchronized(cacheLock) { _cachedFiles.toList() }

    private val _cachedFilesByUri = mutableMapOf<String, MediaFileInfo>()
    fun getFileIndexByUri(): Map<String, MediaFileInfo> = synchronized(cacheLock) { _cachedFilesByUri.toMap() }

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
        deepScan: Boolean = false,
        onProgress: ((songsFound: Int, foldersScanned: Int) -> Unit)? = null
    ): ScanStats {
        clearCache()
        maxFileLimit = maxFiles.coerceAtLeast(1)
        foldersScanned = 0
        songsFound = 0
        val startTime = android.os.SystemClock.elapsedRealtime()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val extensionCounts = mutableMapOf<String, Int>()
        val skippedReasons = mutableMapOf<String, Int>()
        var probedCandidates = 0

        // Phase 1: Walk tree to collect file candidates (fast - content provider queries only)
        val candidates = mutableListOf<FileCandidate>()
        val parentContext = coroutineContext
        val shouldContinue: () -> Boolean = { parentContext.isActive }
        walkTree(
            context = context,
            treeUri = treeUri,
            rootDocumentId = rootDocumentId,
            candidates = candidates,
            shouldContinue = shouldContinue,
            deepScan = deepScan,
            skippedReasons = skippedReasons
        )
        onProgress?.invoke(candidates.size, foldersScanned)

        // Phase 2: Extract metadata in parallel batches
        val metadataDispatcher = Dispatchers.IO.limitedParallelism(METADATA_PARALLELISM)
        var processed = 0
        for (batch in candidates.chunked(METADATA_BATCH_SIZE)) {
            val results = coroutineScope {
                batch.map { candidate ->
                    async(metadataDispatcher) {
                        val metadata = MediaMetadataHelper.extractMetadata(context, candidate.uri.toString())
                        if (candidate.requiresProbe) {
                            if (!looksLikeAudioMetadata(metadata)) {
                                return@async null
                            }
                        }
                        val fallbackYear = candidate.lastModified?.let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .year
                        }
                        val yearValue = metadata?.year?.toIntOrNull() ?: fallbackYear
                        val durationMs = metadata?.durationMs?.toLongOrNull()
                        val resolvedTitle = metadata?.title ?: candidate.name.substringBeforeLast('.')
                        if (metadata?.title == null) {
                            android.util.Log.w("MediaCacheService", "No metadata title for ${candidate.name} (uri=${candidate.uri}), using fallback: $resolvedTitle")
                        }
                        MediaFileInfo(
                            uriString = candidate.uri.toString(),
                            displayName = candidate.name,
                            sizeBytes = candidate.size,
                            title = resolvedTitle,
                            artist = metadata?.artist,
                            album = metadata?.album?.ifBlank { null } ?: candidate.parentFolderName,
                            genre = normalizeGenre(
                                metadata?.genre ?: inferGenreFromPath(candidate.parentFolderName)
                            ),
                            durationMs = durationMs,
                            year = yearValue,
                            addedAtMs = candidate.lastModified
                        )
                    }
                }.awaitAll()
            }
            probedCandidates += batch.count { it.requiresProbe }
            val accepted = results.filterNotNull()
            val rejected = results.size - accepted.size
            if (rejected > 0) {
                skippedReasons["probe_not_audio"] = (skippedReasons["probe_not_audio"] ?: 0) + rejected
            }
            synchronized(cacheLock) {
                _cachedFiles.addAll(accepted)
                _cachedFilesByUri.putAll(accepted.associateBy { it.uriString })
                albumArtistIndexed = false
            }
            for (result in accepted) {
                processed += 1
                songsFound = processed
                val ext = fileExtension(result.displayName)
                extensionCounts[ext] = (extensionCounts[ext] ?: 0) + 1
                onProgress?.invoke(songsFound, foldersScanned)
            }
            if (songsFound >= maxFileLimit) break
        }

        val durationMs = android.os.SystemClock.elapsedRealtime() - startTime
        return ScanStats(
            durationMs = durationMs,
            foldersScanned = foldersScanned,
            songsFound = songsFound,
            extensionCounts = extensionCounts.toMap(),
            skippedReasons = skippedReasons.toMap(),
            deepScan = deepScan,
            probedCandidates = probedCandidates
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
            val genre = it.genre ?: normalizeGenre(
                inferGenreFromPath(Uri.decode(it.uriString))
            )
            MediaFileInfo(
                uriString = it.uriString,
                displayName = it.displayName,
                sizeBytes = it.sizeBytes,
                title = it.title,
                artist = it.artist,
                album = it.album,
                genre = genre,
                durationMs = it.durationMs,
                year = it.year,
                addedAtMs = it.addedAtMs
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
            _cachedFilesByUri.clear()
            _cachedFilesByUri.putAll(files.associateBy { it.uriString })
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
                year = it.year,
                addedAtMs = it.addedAtMs
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
                _cachedFilesByUri[fileInfo.uriString] = fileInfo
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
        val lastModified: Long?,
        val parentFolderName: String?,
        val requiresProbe: Boolean
    )

    private fun walkTree(
        context: Context,
        treeUri: Uri,
        rootDocumentId: String,
        candidates: MutableList<FileCandidate>,
        shouldContinue: () -> Boolean,
        deepScan: Boolean,
        skippedReasons: MutableMap<String, Int>
    ) {
        val contentResolver = context.contentResolver
        val toVisit = ArrayDeque<Pair<String, String?>>()
        toVisit.add(rootDocumentId to null)
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
            if (candidates.size >= effectiveCandidateLimit(deepScan)) return

            val (documentId, parentFolderName) = toVisit.removeFirst()
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
                if (candidates.size >= effectiveCandidateLimit(deepScan)) return

                    val childId = it.getString(idIndex)
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val mimeType = it.getString(mimeIndex)
                    val size = if (it.isNull(sizeIndex)) 0L else it.getLong(sizeIndex)
                    val lastModified =
                        if (it.isNull(modifiedIndex)) null else it.getLong(modifiedIndex)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        toVisit.add(childId to name)
                        foldersScanned += 1
                        continue
                    }

                    val lowerName = name.lowercase(Locale.US)
                    val isPlaylist = isSupportedPlaylistFile(lowerName, mimeType)
                    if (isPlaylist &&
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
                        continue
                    }

                    val isAudio = isSupportedAudioFile(lowerName, mimeType)
                    addFileCandidateIfNeeded(
                        isAudio = isAudio,
                        deepScan = deepScan,
                        treeUri = treeUri,
                        childId = childId,
                        name = name,
                        size = size,
                        lastModified = lastModified,
                        parentFolderName = parentFolderName,
                        candidates = candidates,
                        skippedReasons = skippedReasons
                    )
                }
            }
        }
    }

    private fun addFileCandidateIfNeeded(
        isAudio: Boolean,
        deepScan: Boolean,
        treeUri: Uri,
        childId: String,
        name: String,
        size: Long,
        lastModified: Long?,
        parentFolderName: String?,
        candidates: MutableList<FileCandidate>,
        skippedReasons: MutableMap<String, Int>
    ) {
        if (isAudio) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            candidates.add(
                FileCandidate(
                    uri = uri,
                    name = name,
                    size = size,
                    lastModified = lastModified,
                    parentFolderName = parentFolderName,
                    requiresProbe = false
                )
            )
        } else if (deepScan) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            candidates.add(
                FileCandidate(
                    uri = uri,
                    name = name,
                    size = size,
                    lastModified = lastModified,
                    parentFolderName = parentFolderName,
                    requiresProbe = true
                )
            )
        } else {
            skippedReasons["unsupported_type"] = (skippedReasons["unsupported_type"] ?: 0) + 1
        }
    }

    internal fun isSupportedAudioFile(nameLowercase: String, mimeType: String?): Boolean {
        val mime = mimeType?.lowercase(Locale.US).orEmpty()
        if (mime.startsWith("audio/")) {
            return !mime.contains("mpegurl") && !mime.contains("x-mpegurl")
        }
        if (mime == "application/ogg" || mime == "application/x-ogg") return true
        return SUPPORTED_AUDIO_EXTENSIONS.any { nameLowercase.endsWith(it) }
    }

    internal fun isSupportedPlaylistFile(nameLowercase: String, mimeType: String?): Boolean {
        val mime = mimeType?.lowercase(Locale.US).orEmpty()
        if (nameLowercase.endsWith(".m3u") || nameLowercase.endsWith(".m3u8") || nameLowercase.endsWith(".pls")) {
            return true
        }
        return mime.contains("mpegurl") || mime.contains("x-mpegurl") || mime == "audio/x-scpls"
    }

    private fun effectiveCandidateLimit(deepScan: Boolean): Int {
        return if (deepScan) maxFileLimit * 4 else maxFileLimit
    }

    private fun looksLikeAudioMetadata(metadata: MediaMetadataInfo?): Boolean {
        if (metadata == null) return false
        val duration = metadata.durationMs?.toLongOrNull() ?: 0L
        if (duration > 0L) return true
        return !metadata.title.isNullOrBlank() ||
            !metadata.artist.isNullOrBlank() ||
            !metadata.album.isNullOrBlank() ||
            !metadata.genre.isNullOrBlank()
    }

    private fun fileExtension(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        if (dot < 0 || dot == displayName.lastIndex) return "(none)"
        return displayName.substring(dot).lowercase(Locale.US)
    }

    fun clearFiles() {
        synchronized(cacheLock) {
            _cachedFiles.clear()
            _cachedFilesByUri.clear()
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
            _cachedFilesByUri.clear()
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
            val genre = bucketGenre(file.genre)
            val decade = decadeLabel(file.year)
            albumIndex.getOrPut(album) { mutableListOf() }.add(file)
            artistIndex.getOrPut(artist) { mutableListOf() }.add(file)
            genreIndex.getOrPut(genre) { mutableListOf() }.add(file)
            decadeIndex.getOrPut(decade) { mutableListOf() }.add(file)
        }
        albumArtistIndexed = true
    }

    fun albums(): List<String> = albumIndex.keys.sorted()

    fun albumsByLatestAddedDesc(): List<String> {
        return albumIndex.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, MutableList<MediaFileInfo>>> { entry ->
                    val latestAddedMs = entry.value.maxOfOrNull { file ->
                        val timestamp = file.addedAtMs ?: Long.MIN_VALUE
                        if (timestamp > 0L) timestamp else Long.MIN_VALUE
                    } ?: Long.MIN_VALUE
                    if (latestAddedMs != Long.MIN_VALUE) {
                        latestAddedMs
                    } else {
                        // Fallback when provider does not expose reliable modified/added timestamps.
                        // Use latest release year as a coarse "newness" proxy to avoid always
                        // collapsing to pure name order.
                        entry.value.maxOfOrNull { (it.year ?: Int.MIN_VALUE).toLong() } ?: Long.MIN_VALUE
                    }
                }.thenBy { it.key.lowercase(Locale.US) }
            )
            .map { it.key }
    }

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
            val haystack = listOfNotNull(
                file.cleanTitle,
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
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return "Other"
        val primary = trimmed
            .split(';', '/', '|', ',')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        return if (primary.isBlank()) "Other" else primary
    }

    private fun inferGenreFromPath(pathLike: String?): String? {
        val normalized = pathLike
            ?.replace('\\', '/')
            ?.lowercase(Locale.US)
            ?.trim()
            .orEmpty()
        if (normalized.isBlank()) return null
        return when {
            normalized.contains("hip hop") || normalized.contains("hip-hop") ||
                normalized.contains("/rap") || normalized.contains("trap") -> "Hip-Hop"
            normalized.contains("r&b") || normalized.contains("rnb") ||
                normalized.contains("soul") || normalized.contains("motown") -> "R&B"
            normalized.contains("electronic") || normalized.contains("edm") ||
                normalized.contains("house") || normalized.contains("techno") ||
                normalized.contains("trance") || normalized.contains("dubstep") -> "Electronic"
            normalized.contains("rock") || normalized.contains("metal") ||
                normalized.contains("punk") || normalized.contains("grunge") -> "Rock"
            normalized.contains("country") || normalized.contains("bluegrass") -> "Country"
            normalized.contains("folk") || normalized.contains("americana") -> "Folk"
            normalized.contains("classical") || normalized.contains("orchestra") ||
                normalized.contains("opera") || normalized.contains("baroque") -> "Classical"
            normalized.contains("jazz") -> "Jazz"
            normalized.contains("blues") -> "Blues"
            normalized.contains("latin") || normalized.contains("reggaeton") ||
                normalized.contains("salsa") || normalized.contains("bachata") -> "Latin"
            normalized.contains("pop") -> "Pop"
            else -> null
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
    val songsFound: Int,
    val extensionCounts: Map<String, Int> = emptyMap(),
    val skippedReasons: Map<String, Int> = emptyMap(),
    val deepScan: Boolean = false,
    val probedCandidates: Int = 0
)
