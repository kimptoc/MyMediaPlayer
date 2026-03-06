package com.example.mymediaplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymediaplayer.shared.MediaCacheService
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import com.example.mymediaplayer.shared.PlaylistService
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class ScanState(
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFileInfo> = emptyList(),
    val discoveredPlaylists: List<PlaylistInfo> = emptyList(),
    val scanMessage: String? = null,
    val scanProgress: String? = null,
    val lastScanLimit: Int = MediaCacheService.MAX_CACHE_SIZE,
    val deepScanEnabled: Boolean = false,
    val folderMessage: String? = null
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentTrackName: String? = null,
    val currentArtistName: String? = null,
    val currentMediaId: String? = null,
    val currentPositionMs: Long = 0L,
    val positionUpdatedAtElapsedMs: Long = 0L,
    val playbackSpeed: Float = 0f,
    val durationMs: Long = 0L,
    val isPlayingPlaylist: Boolean = false,
    val queueTitle: String? = null,
    val queuePosition: String? = null,
    val queueItems: List<QueueEntry> = emptyList(),
    val activeQueueId: Long = -1L,
    val repeatMode: Int = 0,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false
)

data class QueueEntry(
    val queueId: Long,
    val mediaId: String?,
    val title: String
)

data class LibraryState(
    val selectedTab: LibraryTab = LibraryTab.Songs,
    val albumSortMode: AlbumSortMode = AlbumSortMode.Name,
    val selectedAlbum: String? = null,
    val selectedGenre: String? = null,
    val selectedArtist: String? = null,
    val selectedDecade: String? = null,
    val filteredSongs: List<MediaFileInfo> = emptyList(),
    val albums: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val decades: List<String> = emptyList(),
    val isMetadataLoading: Boolean = false
)

data class SearchState(
    val searchQuery: String = "",
    val searchResults: List<MediaFileInfo> = emptyList()
)

data class PlaylistMgmtState(
    val selectedPlaylist: PlaylistInfo? = null,
    val playlistSongs: List<MediaFileInfo> = emptyList(),
    val isPlaylistLoading: Boolean = false,
    val lastPlaylistCount: Int = 3,
    val manualPlaylistSongs: List<MediaFileInfo> = emptyList(),
    val playlistMessage: String? = null
)

data class MainUiState(
    val scan: ScanState = ScanState(),
    val playback: PlaybackState = PlaybackState(),
    val library: LibraryState = LibraryState(),
    val search: SearchState = SearchState(),
    val playlist: PlaylistMgmtState = PlaylistMgmtState(),
    val favoriteUris: Set<String> = emptySet(),
    val playCounts: Map<String, Int> = emptyMap(),
    val lastPlayedAt: Map<String, Long> = emptyMap()
)

enum class LibraryTab(val label: String) {
    Songs("Songs"),
    Playlists("Playlists"),
    Albums("Albums"),
    Genres("Genres"),
    Artists("Artists"),
    Decades("Decades")
}

enum class AlbumSortMode(val label: String) {
    Name("Name"),
    DateAddedDesc("Date Added")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val PREFS_NAME = "mymediaplayer_prefs"
        private const val KEY_FAVORITE_URIS = "favorite_uris"
        private const val KEY_PLAY_COUNTS = "play_counts"
        private const val KEY_LAST_PLAYED_AT = "last_played_at"
        const val SMART_PREFIX = "smart:"
        const val SMART_FAVORITES = "${SMART_PREFIX}favorites"
        const val SMART_RECENTLY_ADDED = "${SMART_PREFIX}recently_added"
        const val SMART_MOST_PLAYED = "${SMART_PREFIX}most_played"
        const val SMART_NOT_HEARD_RECENTLY = "${SMART_PREFIX}not_heard_recently"
    }

    private val mediaCacheService = MediaCacheService()
    private val playlistService = PlaylistService()
    private val scanCache =
        mutableMapOf<String, Pair<List<MediaFileInfo>, List<PlaylistInfo>>>()
    private var treeUri: Uri? = null
    private var playlistTreeUri: Uri? = null
    private var metadataKey: String? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        val prefs = getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
        val favorites = prefs.getStringSet(KEY_FAVORITE_URIS, emptySet())?.toSet() ?: emptySet()
        val playCounts = decodePlayCounts(prefs.getString(KEY_PLAY_COUNTS, null))
        val lastPlayedAt = decodeLastPlayedAt(prefs.getString(KEY_LAST_PLAYED_AT, null))
        _uiState.value = _uiState.value.copy(
            favoriteUris = favorites,
            playCounts = playCounts,
            lastPlayedAt = lastPlayedAt
        )
    }

    override fun onCleared() {
        super.onCleared()
        mediaCacheService.clearCache()
        scanCache.clear()
    }

    internal fun resetAfterScan(
        files: List<MediaFileInfo>,
        playlists: List<PlaylistInfo>,
        maxFiles: Int,
        deepScan: Boolean = false,
        scanMessage: String? = null,
        scanProgress: String? = null
    ): MainUiState {
        val current = _uiState.value
        return current.copy(
            scan = current.scan.copy(
                isScanning = false,
                scannedFiles = files,
                discoveredPlaylists = sortPlaylists(playlists),
                lastScanLimit = maxFiles,
                deepScanEnabled = deepScan,
                scanMessage = scanMessage,
                scanProgress = scanProgress
            ),
            library = LibraryState(),
            playlist = current.playlist.copy(
                selectedPlaylist = null,
                playlistSongs = emptyList(),
                isPlaylistLoading = false,
                manualPlaylistSongs = emptyList()
            ),
            search = current.search.copy(
                searchResults = applySearchResults(files, current.search.searchQuery)
            )
        )
    }

    fun onDirectorySelected(
        treeUri: Uri,
        maxFiles: Int,
        deepScan: Boolean = false,
        forceRescan: Boolean = false
    ) {
        val key = "${treeUri}|$maxFiles|deep=$deepScan"
        if (!forceRescan) {
            val cached = scanCache[key]
            if (cached != null) {
                mediaCacheService.clearCache()
                cached.first.forEach { mediaCacheService.addFile(it) }
                cached.second.forEach { mediaCacheService.addPlaylist(it) }
                _uiState.value = resetAfterScan(cached.first, cached.second, maxFiles, deepScan)
                reimportPlaylistsFromSaveFolderIfNeeded()
                metadataKey = null
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!forceRescan && !deepScan) {
                val persisted =
                    mediaCacheService.loadPersistedCache(getApplication(), treeUri, maxFiles)
                if (persisted != null) {
                    scanCache[key] = persisted.files to persisted.playlists
                    _uiState.value = resetAfterScan(
                        persisted.files,
                        persisted.playlists,
                        maxFiles,
                        deepScan = false
                    )
                    reimportPlaylistsFromSaveFolderIfNeeded()
                    metadataKey = null
                    return@launch
                }
            }
            val current = _uiState.value
            _uiState.value = current.copy(
                scan = current.scan.copy(
                    isScanning = true,
                    scannedFiles = emptyList(),
                    discoveredPlaylists = emptyList(),
                    lastScanLimit = maxFiles,
                    deepScanEnabled = deepScan,
                    scanMessage = null,
                    scanProgress = "Scanning… 0 songs"
                ),
                library = LibraryState(),
                playlist = current.playlist.copy(
                    selectedPlaylist = null,
                    playlistSongs = emptyList(),
                    isPlaylistLoading = false
                )
            )
            val stats = mediaCacheService.scanDirectory(
                getApplication(),
                treeUri,
                maxFiles,
                deepScan
            ) { songsFound, foldersScanned ->
                val cur = _uiState.value
                _uiState.value = cur.copy(
                    scan = cur.scan.copy(
                        scanProgress = "Scanning… $songsFound songs • $foldersScanned folders",
                        scannedFiles = mediaCacheService.cachedFiles
                    )
                )
            }
            val files = mediaCacheService.cachedFiles
            val playlists = mediaCacheService.discoveredPlaylists
            scanCache[key] = files to playlists
            if (!deepScan) {
                mediaCacheService.persistCache(getApplication(), treeUri, maxFiles)
            }
            val seconds = stats.durationMs / 1000.0
            val typeSummary = stats.extensionCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .joinToString(", ") { "${it.key}:${it.value}" }
                .ifBlank { "n/a" }
            val skipSummary = if (stats.skippedReasons.isEmpty()) {
                "none"
            } else {
                stats.skippedReasons.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .joinToString(", ") { "${it.key}:${it.value}" }
            }
            val modeLabel = if (stats.deepScan) "Deep" else "Normal"
            val message =
                "$modeLabel scan complete in %.1fs • Folders: %d • Songs: %d • Types: %s • Skipped: %s • Probed: %d"
                    .format(
                        seconds,
                        stats.foldersScanned,
                        stats.songsFound,
                        typeSummary,
                        skipSummary,
                        stats.probedCandidates
                    )
            _uiState.value = resetAfterScan(
                files,
                playlists,
                maxFiles,
                deepScan = deepScan,
                scanMessage = message
            )
            reimportPlaylistsFromSaveFolderIfNeeded()
            metadataKey = null
        }
    }

    fun scanWholeDevice(maxFiles: Int, forceRescan: Boolean = false) {
        val key = "whole_device|$maxFiles"
        if (!forceRescan) {
            val cached = scanCache[key]
            if (cached != null) {
                mediaCacheService.clearCache()
                cached.first.forEach { mediaCacheService.addFile(it) }
                cached.second.forEach { mediaCacheService.addPlaylist(it) }
                _uiState.value = resetAfterScan(
                    cached.first,
                    cached.second,
                    maxFiles,
                    deepScan = false,
                    scanMessage = "Whole-drive scan loaded from cache"
                )
                reimportPlaylistsFromSaveFolderIfNeeded()
                metadataKey = null
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value
            _uiState.value = current.copy(
                scan = current.scan.copy(
                    isScanning = true,
                    scannedFiles = emptyList(),
                    discoveredPlaylists = emptyList(),
                    lastScanLimit = maxFiles,
                    deepScanEnabled = false,
                    scanMessage = null,
                    scanProgress = "Scanning whole drive… 0 songs"
                ),
                library = LibraryState(),
                playlist = current.playlist.copy(
                    selectedPlaylist = null,
                    playlistSongs = emptyList(),
                    isPlaylistLoading = false
                )
            )
            val stats = mediaCacheService.scanWholeDevice(
                getApplication(),
                maxFiles
            ) { songsFound, _ ->
                val cur = _uiState.value
                _uiState.value = cur.copy(
                    scan = cur.scan.copy(
                        scanProgress = "Scanning whole drive… $songsFound songs",
                        scannedFiles = mediaCacheService.cachedFiles
                    )
                )
            }
            val files = mediaCacheService.cachedFiles
            val playlists = mediaCacheService.discoveredPlaylists
            scanCache[key] = files to playlists
            val seconds = stats.durationMs / 1000.0
            val typeSummary = stats.extensionCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .joinToString(", ") { "${it.key}:${it.value}" }
                .ifBlank { "n/a" }
            val message =
                "Whole-drive scan complete in %.1fs • Songs: %d • Types: %s"
                    .format(seconds, stats.songsFound, typeSummary)
            _uiState.value = resetAfterScan(
                files,
                playlists,
                maxFiles,
                deepScan = false,
                scanMessage = message
            )
            reimportPlaylistsFromSaveFolderIfNeeded()
            metadataKey = null
        }
    }

    fun setTreeUri(uri: Uri) {
        treeUri = uri
        metadataKey = null
    }

    fun setPlaylistTreeUri(uri: Uri, showMessage: Boolean = true) {
        playlistTreeUri = uri
        importPlaylistsFromSaveFolder(uri, showMessage = showMessage)
    }

    private fun resolvePlaylistTreeUri(): Uri? = playlistTreeUri ?: treeUri

    private fun importPlaylistsFromSaveFolder(uri: Uri, showMessage: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = playlistService.listPlaylistsInTree(getApplication(), uri)
            if (imported.isEmpty()) return@launch
            val current = _uiState.value
            val merged = sortPlaylists((current.scan.discoveredPlaylists + imported).distinctBy { it.uriString })
            mediaCacheService.clearPlaylists()
            merged.forEach { mediaCacheService.addPlaylist(it) }
            _uiState.value = current.copy(
                scan = current.scan.copy(discoveredPlaylists = merged),
                playlist = current.playlist.copy(
                    playlistMessage = if (showMessage) {
                        "Loaded ${imported.size} playlist(s) from save folder"
                    } else {
                        current.playlist.playlistMessage
                    }
                )
            )
            val key = treeUri?.let { "${it}|${current.scan.lastScanLimit}|deep=${current.scan.deepScanEnabled}" }
            if (key != null) {
                val cached = scanCache[key]
                if (cached != null) {
                    scanCache[key] = cached.first to merged
                }
            }
        }
    }

    private fun reimportPlaylistsFromSaveFolderIfNeeded() {
        val uri = playlistTreeUri ?: return
        importPlaylistsFromSaveFolder(uri, showMessage = false)
    }

    fun selectTab(tab: LibraryTab) {
        val current = _uiState.value
        val lib = current.library
        _uiState.value = current.copy(
            library = lib.copy(
                selectedTab = tab,
                selectedAlbum = if (tab == LibraryTab.Albums) lib.selectedAlbum else null,
                selectedGenre = if (tab == LibraryTab.Genres) lib.selectedGenre else null,
                selectedArtist = if (tab == LibraryTab.Artists) lib.selectedArtist else null,
                selectedDecade = if (tab == LibraryTab.Decades) lib.selectedDecade else null,
                filteredSongs = if (
                    tab == LibraryTab.Albums ||
                    tab == LibraryTab.Genres ||
                    tab == LibraryTab.Artists ||
                    tab == LibraryTab.Decades
                ) {
                    lib.filteredSongs
                } else {
                    emptyList()
                }
            )
        )
        if (
            tab == LibraryTab.Albums ||
            tab == LibraryTab.Genres ||
            tab == LibraryTab.Artists ||
            tab == LibraryTab.Decades
        ) {
            ensureMetadataLoaded()
        }
    }

    fun selectAlbum(album: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            library = current.library.copy(
                selectedAlbum = album,
                selectedGenre = null,
                selectedArtist = null
            )
        )
        applyFilteredSongs()
    }

    fun selectGenre(genre: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            library = current.library.copy(
                selectedAlbum = null,
                selectedGenre = genre,
                selectedArtist = null
            )
        )
        applyFilteredSongs()
    }

    fun selectArtist(artist: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            library = current.library.copy(
                selectedAlbum = null,
                selectedGenre = null,
                selectedArtist = artist,
                selectedDecade = null
            )
        )
        applyFilteredSongs()
    }

    fun selectDecade(decade: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            library = current.library.copy(
                selectedAlbum = null,
                selectedGenre = null,
                selectedArtist = null,
                selectedDecade = decade
            )
        )
        applyFilteredSongs()
    }

    fun setAlbumSortMode(mode: AlbumSortMode) {
        val current = _uiState.value
        if (current.library.albumSortMode == mode) return
        val sortedAlbums = if (mediaCacheService.hasAlbumArtistIndexes()) {
            sortedAlbumsForMode(mode)
        } else {
            current.library.albums
        }
        _uiState.value = current.copy(
            library = current.library.copy(
                albumSortMode = mode,
                albums = sortedAlbums
            )
        )
    }

    fun clearCategorySelection() {
        val current = _uiState.value
        _uiState.value = current.copy(
            library = current.library.copy(
                selectedAlbum = null,
                selectedGenre = null,
                selectedArtist = null,
                selectedDecade = null,
                filteredSongs = emptyList()
            )
        )
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value
        val results = applySearchResults(current.scan.scannedFiles, query)
        _uiState.value = current.copy(
            search = current.search.copy(
                searchQuery = query,
                searchResults = results
            )
        )
    }

    fun clearSearch() {
        val current = _uiState.value
        _uiState.value = current.copy(
            search = current.search.copy(
                searchQuery = "",
                searchResults = emptyList()
            )
        )
    }

    fun addToManualPlaylist(file: MediaFileInfo) {
        val current = _uiState.value
        if (current.playlist.manualPlaylistSongs.any { it.uriString == file.uriString }) return
        _uiState.value = current.copy(
            playlist = current.playlist.copy(
                manualPlaylistSongs = current.playlist.manualPlaylistSongs + file
            )
        )
    }

    fun addManyToManualPlaylist(files: List<MediaFileInfo>) {
        if (files.isEmpty()) return
        val current = _uiState.value
        val existingUris = current.playlist.manualPlaylistSongs.map { it.uriString }.toMutableSet()
        val additions = files.filter { existingUris.add(it.uriString) }
        if (additions.isEmpty()) return
        _uiState.value = current.copy(
            playlist = current.playlist.copy(
                manualPlaylistSongs = current.playlist.manualPlaylistSongs + additions
            )
        )
    }

    fun clearManualPlaylist() {
        val current = _uiState.value
        _uiState.value = current.copy(
            playlist = current.playlist.copy(manualPlaylistSongs = emptyList())
        )
    }

    fun createManualPlaylist(name: String) {
        val uri = resolvePlaylistTreeUri()
        val current = _uiState.value
        if (uri == null) {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Select a folder first.")
            )
            return
        }
        if (current.playlist.manualPlaylistSongs.isEmpty()) {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Add songs first.")
            )
            return
        }
        val result = playlistService.writePlaylistWithName(
            getApplication(),
            uri,
            current.playlist.manualPlaylistSongs,
            name
        )
        if (result != null) {
            val updatedPlaylists = sortPlaylists(current.scan.discoveredPlaylists + result)
            val key = treeUri?.let { "${it}|${current.scan.lastScanLimit}" }
            if (key != null) {
                val cached = scanCache[key]
                if (cached != null) {
                    scanCache[key] = cached.first to (cached.second + result)
                }
            }
            mediaCacheService.addPlaylist(result)
            _uiState.value = current.copy(
                scan = current.scan.copy(discoveredPlaylists = updatedPlaylists),
                playlist = current.playlist.copy(
                    playlistMessage = "Created ${result.displayName}",
                    manualPlaylistSongs = emptyList()
                )
            )
            treeUri?.let { tree ->
                val limit = current.scan.lastScanLimit
                viewModelScope.launch(Dispatchers.IO) {
                    mediaCacheService.persistCache(getApplication(), tree, limit)
                }
            }
        } else {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Failed to create playlist")
            )
        }
    }

    fun createPlaylistFromSongs(name: String, files: List<MediaFileInfo>): PlaylistInfo? {
        val uri = resolvePlaylistTreeUri()
        val current = _uiState.value
        if (uri == null) {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Select a folder first.")
            )
            return null
        }
        if (files.isEmpty()) {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Add songs first.")
            )
            return null
        }

        val trimmed = name.trim()
        val resolvedName = if (trimmed.isNotEmpty()) trimmed else "playlist_${System.currentTimeMillis()}"
        val result = playlistService.writePlaylistWithName(
            getApplication(),
            uri,
            files,
            resolvedName
        )

        if (result != null) {
            val updatedPlaylists = sortPlaylists(current.scan.discoveredPlaylists + result)
            val key = treeUri?.let { "${it}|${current.scan.lastScanLimit}" }
            if (key != null) {
                val cached = scanCache[key]
                if (cached != null) {
                    scanCache[key] = cached.first to (cached.second + result)
                }
            }
            mediaCacheService.addPlaylist(result)
            _uiState.value = current.copy(
                scan = current.scan.copy(discoveredPlaylists = updatedPlaylists),
                playlist = current.playlist.copy(
                    playlistMessage = "Created ${result.displayName}"
                )
            )
            treeUri?.let { tree ->
                val limit = current.scan.lastScanLimit
                viewModelScope.launch(Dispatchers.IO) {
                    mediaCacheService.persistCache(getApplication(), tree, limit)
                }
            }
            return result
        } else {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Failed to create playlist")
            )
            return null
        }
    }

    fun renamePlaylist(playlist: PlaylistInfo, newName: String) {
        val requestedName = newName.trim()
        if (requestedName.isEmpty()) {
            val current = _uiState.value
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Enter a playlist name")
            )
            return
        }
        val current = _uiState.value
        val renamedDirect = playlistService.renamePlaylist(
            getApplication(),
            Uri.parse(playlist.uriString),
            requestedName
        )
        var usedFallback = false
        var oldDeleteFailed = false
        val renamed = if (renamedDirect != null) {
            renamedDirect
        } else {
            val tree = treeUri
            if (tree == null) {
                null
            } else {
                usedFallback = true
                val oldUri = Uri.parse(playlist.uriString)
                val existingSongs = playlistService.readPlaylist(getApplication(), oldUri)
                val recreated = playlistService.writePlaylistWithName(
                    getApplication(),
                    tree,
                    existingSongs,
                    requestedName
                )
                if (recreated != null) {
                    val deletedOld = playlistService.deletePlaylist(
                        getApplication(),
                        oldUri,
                        playlist.displayName,
                        tree
                    )
                    oldDeleteFailed = !deletedOld
                }
                recreated
            }
        }
        if (renamed == null) {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Failed to rename playlist")
            )
            return
        }

        val latest = _uiState.value
        var replaced = false
        val updatedPlaylists = latest.scan.discoveredPlaylists.map { existing ->
            val isTarget = existing.uriString == playlist.uriString ||
                existing.displayName == playlist.displayName
            if (isTarget) {
                replaced = true
                renamed
            } else {
                existing
            }
        }.let {
            if (replaced) it else {
                // Fallback for providers that mutate URI/name unexpectedly during rename.
                it.filterNot { p ->
                    p.displayName == playlist.displayName ||
                        p.displayName.removeSuffix(".m3u") == playlist.displayName.removeSuffix(".m3u")
                } + renamed
            }
        }.let(::sortPlaylists)
        val key = treeUri?.let { "${it}|${latest.scan.lastScanLimit}" }
        if (key != null) {
            val cached = scanCache[key]
            if (cached != null) {
                scanCache[key] = cached.first to updatedPlaylists
            }
        }
        mediaCacheService.removePlaylistByUri(playlist.uriString)
        mediaCacheService.addPlaylist(renamed)
        _uiState.value = latest.copy(
            scan = latest.scan.copy(discoveredPlaylists = updatedPlaylists),
            playlist = latest.playlist.copy(
                selectedPlaylist = if (
                    latest.playlist.selectedPlaylist?.uriString == playlist.uriString ||
                    latest.playlist.selectedPlaylist?.displayName == playlist.displayName
                ) {
                    renamed
                } else {
                    latest.playlist.selectedPlaylist
                },
                playlistMessage = if (usedFallback && oldDeleteFailed) {
                    "Renamed, but couldn't delete old file"
                } else {
                    "Renamed to ${renamed.displayName.removeSuffix(".m3u")}"
                }
            )
        )
        treeUri?.let { tree ->
            val limit = latest.scan.lastScanLimit
            viewModelScope.launch(Dispatchers.IO) {
                mediaCacheService.persistCache(getApplication(), tree, limit)
            }
        }
    }

    fun addSongToExistingPlaylist(playlist: PlaylistInfo, file: MediaFileInfo) {
        val uri = Uri.parse(playlist.uriString)
        val success = playlistService.appendToPlaylist(getApplication(), uri, listOf(file))
        val current = _uiState.value
        if (success) {
            val updatedSongs = if (current.playlist.selectedPlaylist?.uriString == playlist.uriString) {
                current.playlist.playlistSongs + file
            } else {
                current.playlist.playlistSongs
            }
            _uiState.value = current.copy(
                playlist = current.playlist.copy(
                    playlistSongs = updatedSongs,
                    playlistMessage = "Added to ${playlist.displayName.removeSuffix(".m3u")}"
                )
            )
        } else {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Failed to update playlist")
            )
        }
    }

    fun addManyToExistingPlaylist(playlist: PlaylistInfo, files: List<MediaFileInfo>) {
        if (files.isEmpty()) return
        val uri = Uri.parse(playlist.uriString)
        val success = playlistService.appendToPlaylist(getApplication(), uri, files)
        val current = _uiState.value
        if (success) {
            val updatedSongs = if (current.playlist.selectedPlaylist?.uriString == playlist.uriString) {
                val existingUris = current.playlist.playlistSongs.map { it.uriString }.toMutableSet()
                val additions = files.filter { existingUris.add(it.uriString) }
                current.playlist.playlistSongs + additions
            } else {
                current.playlist.playlistSongs
            }
            _uiState.value = current.copy(
                playlist = current.playlist.copy(
                    playlistSongs = updatedSongs,
                    playlistMessage = "Added ${files.size} song(s) to ${playlist.displayName.removeSuffix(".m3u")}"
                )
            )
        } else {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Failed to update playlist")
            )
        }
    }

    fun savePlaylistEdits(playlist: PlaylistInfo, songs: List<MediaFileInfo>) {
        if (songs.isEmpty()) {
            val current = _uiState.value
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Playlist cannot be empty")
            )
            return
        }
        val uri = Uri.parse(playlist.uriString)
        val success = playlistService.overwritePlaylist(getApplication(), uri, songs)
        val current = _uiState.value
        if (success) {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(
                    playlistSongs = if (current.playlist.selectedPlaylist?.uriString == playlist.uriString) {
                        songs
                    } else {
                        current.playlist.playlistSongs
                    },
                    playlistMessage = "Saved ${playlist.displayName.removeSuffix(".m3u")}"
                )
            )
        } else {
            _uiState.value = current.copy(
                playlist = current.playlist.copy(playlistMessage = "Failed to save playlist")
            )
        }
    }

    fun deletePlaylist(playlist: PlaylistInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(playlist.uriString)
            val success = playlistService.deletePlaylist(
                getApplication(),
                uri,
                playlist.displayName,
                treeUri
            )
            val current = _uiState.value
            if (success) {
                val updatedPlaylists = sortPlaylists(current.scan.discoveredPlaylists.filterNot {
                    it.uriString == playlist.uriString
                })
                val key = treeUri?.let { "${it}|${current.scan.lastScanLimit}" }
                if (key != null) {
                    val cached = scanCache[key]
                    if (cached != null) {
                        scanCache[key] = cached.first to updatedPlaylists
                    }
                }
                mediaCacheService.removePlaylistByUri(playlist.uriString)
                _uiState.value = current.copy(
                    scan = current.scan.copy(discoveredPlaylists = updatedPlaylists),
                    playlist = current.playlist.copy(
                        selectedPlaylist = if (current.playlist.selectedPlaylist?.uriString == playlist.uriString) {
                            null
                        } else {
                            current.playlist.selectedPlaylist
                        },
                        playlistSongs = if (current.playlist.selectedPlaylist?.uriString == playlist.uriString) {
                            emptyList()
                        } else {
                            current.playlist.playlistSongs
                        },
                        playlistMessage = "Deleted ${playlist.displayName.removeSuffix(".m3u")}"
                    )
                )
                treeUri?.let { tree ->
                    val limit = current.scan.lastScanLimit
                    mediaCacheService.persistCache(getApplication(), tree, limit)
                }
            } else {
                _uiState.value = current.copy(
                    playlist = current.playlist.copy(playlistMessage = "Failed to delete playlist")
                )
            }
        }
    }

    fun selectPlaylist(playlist: PlaylistInfo) {
        if (playlist.uriString.startsWith(SMART_PREFIX)) {
            val current = _uiState.value
            _uiState.value = current.copy(
                playlist = current.playlist.copy(
                    selectedPlaylist = playlist,
                    playlistSongs = smartPlaylistSongs(playlist.uriString),
                    isPlaylistLoading = false
                )
            )
            return
        }
        val current = _uiState.value
        _uiState.value = current.copy(
            playlist = current.playlist.copy(
                selectedPlaylist = playlist,
                playlistSongs = emptyList(),
                isPlaylistLoading = true
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            val songs = playlistService.readPlaylist(getApplication(), Uri.parse(playlist.uriString))
            val cur = _uiState.value
            val byUri = cur.scan.scannedFiles.associateBy { it.uriString }
            val enriched = songs.map { song -> byUri[song.uriString] ?: song }
            _uiState.value = cur.copy(
                playlist = cur.playlist.copy(
                    playlistSongs = enriched,
                    isPlaylistLoading = false
                )
            )
        }
    }

    fun clearSelectedPlaylist() {
        val current = _uiState.value
        _uiState.value = current.copy(
            playlist = current.playlist.copy(
                selectedPlaylist = null,
                playlistSongs = emptyList(),
                isPlaylistLoading = false
            )
        )
    }

    fun createRandomPlaylist(count: Int) {
        val uri = resolvePlaylistTreeUri() ?: return
        val files = _uiState.value.scan.scannedFiles
        if (files.isEmpty()) return
        val safeCount = count.coerceIn(1, files.size)
        val selected = files.shuffled().take(safeCount)
        viewModelScope.launch(Dispatchers.IO) {
            val result = playlistService.writePlaylist(getApplication(), uri, selected)
            val current = _uiState.value
            if (result != null) {
                val updatedPlaylists = sortPlaylists(current.scan.discoveredPlaylists + result)
                _uiState.value = current.copy(
                    scan = current.scan.copy(discoveredPlaylists = updatedPlaylists),
                    playlist = current.playlist.copy(
                        playlistMessage = "Created ${result.displayName}",
                        lastPlaylistCount = safeCount
                    )
                )
                val key = uri.toString()
                val existing = scanCache[key]
                if (existing != null) {
                    scanCache[key] = existing.first to updatedPlaylists
                }
            } else {
                _uiState.value = current.copy(
                    playlist = current.playlist.copy(
                        playlistMessage = "Failed to create playlist",
                        lastPlaylistCount = safeCount
                    )
                )
            }
        }
    }

    fun clearPlaylistMessage() {
        val current = _uiState.value
        _uiState.value = current.copy(
            playlist = current.playlist.copy(playlistMessage = null)
        )
    }

    fun setFolderMessage(message: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            scan = current.scan.copy(folderMessage = message)
        )
    }

    fun clearFolderMessage() {
        val current = _uiState.value
        _uiState.value = current.copy(
            scan = current.scan.copy(folderMessage = null)
        )
    }

    fun clearScanMessage() {
        val current = _uiState.value
        _uiState.value = current.copy(
            scan = current.scan.copy(scanMessage = null)
        )
    }

    fun toggleFavorite(uriString: String) {
        val current = _uiState.value
        val updated = if (uriString in current.favoriteUris) {
            current.favoriteUris - uriString
        } else {
            current.favoriteUris + uriString
        }
        _uiState.value = current.copy(favoriteUris = updated)
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_FAVORITE_URIS, updated)
            .apply()
        refreshSmartPlaylistSelection()
    }

    fun updatePlaybackState(
        state: Int,
        mediaId: String?,
        trackName: String?,
        artistName: String?,
        positionMs: Long,
        positionUpdatedAtElapsedMs: Long,
        playbackSpeed: Float,
        durationMs: Long
    ) {
        val current = _uiState.value
        var updatedPlayCounts = current.playCounts
        var updatedLastPlayedAt = current.lastPlayedAt
        if (
            state == PlaybackStateCompat.STATE_PLAYING &&
            !mediaId.isNullOrBlank() &&
            mediaId != current.playback.currentMediaId
        ) {
            val next = (current.playCounts[mediaId] ?: 0) + 1
            updatedPlayCounts = current.playCounts + (mediaId to next)
            updatedLastPlayedAt = current.lastPlayedAt + (mediaId to System.currentTimeMillis())
            persistPlayCounts(updatedPlayCounts)
            persistLastPlayedAt(updatedLastPlayedAt)
        }
        _uiState.value = current.copy(
            playback = current.playback.copy(
                isPlaying = (state == PlaybackStateCompat.STATE_PLAYING),
                isPaused = (state == PlaybackStateCompat.STATE_PAUSED),
                currentTrackName = if (state == PlaybackStateCompat.STATE_STOPPED) null else trackName,
                currentArtistName = if (state == PlaybackStateCompat.STATE_STOPPED) null else artistName,
                currentMediaId = if (state == PlaybackStateCompat.STATE_STOPPED) null else mediaId,
                currentPositionMs = if (state == PlaybackStateCompat.STATE_STOPPED) 0L else positionMs,
                positionUpdatedAtElapsedMs = positionUpdatedAtElapsedMs,
                playbackSpeed = playbackSpeed,
                durationMs = if (state == PlaybackStateCompat.STATE_STOPPED) 0L else durationMs
            ),
            playCounts = updatedPlayCounts,
            lastPlayedAt = updatedLastPlayedAt
        )
        refreshSmartPlaylistSelection()
    }

    fun updateQueueState(queueTitle: String?, queueSize: Int, activeIndex: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(
            playback = current.playback.copy(
                isPlayingPlaylist = queueTitle != null,
                queueTitle = queueTitle,
                queuePosition = if (queueTitle != null && queueSize > 0 && activeIndex >= 0) {
                    "${activeIndex + 1}/$queueSize"
                } else {
                    null
                },
                hasPrev = queueTitle != null && activeIndex > 0,
                hasNext = queueTitle != null && activeIndex >= 0 && activeIndex < queueSize - 1
            )
        )
    }

    fun updateQueueState(
        queueTitle: String?,
        queueItems: List<QueueEntry>,
        activeQueueId: Long
    ) {
        val activeIndex = queueItems.indexOfFirst { it.queueId == activeQueueId }
        updateQueueState(queueTitle, queueItems.size, activeIndex)
        val current = _uiState.value
        _uiState.value = current.copy(
            playback = current.playback.copy(
                queueItems = queueItems,
                activeQueueId = activeQueueId
            )
        )
    }

    fun updateRepeatMode(mode: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(
            playback = current.playback.copy(repeatMode = mode)
        )
    }

    private fun ensureMetadataLoaded() {
        val current = _uiState.value
        val sourceKey = treeUri?.toString() ?: "whole_device:${current.scan.scannedFiles.size}"
        if (metadataKey == sourceKey && mediaCacheService.hasAlbumArtistIndexes()) return

        viewModelScope.launch(Dispatchers.IO) {
            val cur = _uiState.value
            _uiState.value = cur.copy(
                library = cur.library.copy(isMetadataLoading = true)
            )
            mediaCacheService.buildAlbumArtistIndexesFromCache()
            val artists = mediaCacheService.artists()
            val genres = mediaCacheService.genres()
            val decades = mediaCacheService.decades()
            metadataKey = sourceKey
            val cur2 = _uiState.value
            _uiState.value = cur2.copy(
                library = cur2.library.copy(
                    albums = sortedAlbumsForMode(cur2.library.albumSortMode),
                    genres = genres,
                    artists = artists,
                    decades = decades,
                    isMetadataLoading = false
                )
            )
            applyFilteredSongs()
        }
    }

    private fun applyFilteredSongs() {
        val current = _uiState.value
        val lib = current.library
        val filtered = when {
            lib.selectedAlbum != null ->
                mediaCacheService.songsForAlbum(lib.selectedAlbum)
            lib.selectedGenre != null ->
                mediaCacheService.songsForGenre(lib.selectedGenre)
            lib.selectedArtist != null ->
                mediaCacheService.songsForArtist(lib.selectedArtist)
            lib.selectedDecade != null ->
                mediaCacheService.songsForDecade(lib.selectedDecade)
            else -> emptyList()
        }
        _uiState.value = current.copy(
            library = lib.copy(filteredSongs = filtered)
        )
    }

    private fun applySearchResults(
        files: List<MediaFileInfo>,
        query: String
    ): List<MediaFileInfo> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        return files.filter { file ->
            val title = file.title ?: file.displayName
            val haystack = listOfNotNull(
                title,
                file.artist,
                file.album,
                file.genre
            ).joinToString(" ").lowercase()
            haystack.contains(needle)
        }
    }

    private fun sortedAlbumsForMode(mode: AlbumSortMode): List<String> {
        return when (mode) {
            AlbumSortMode.Name -> mediaCacheService.albums()
            AlbumSortMode.DateAddedDesc -> mediaCacheService.albumsByLatestAddedDesc()
        }
    }

    private fun sortPlaylists(playlists: List<PlaylistInfo>): List<PlaylistInfo> {
        return playlists.sortedBy { it.displayName.lowercase(Locale.US) }
    }

    private fun smartPlaylistSongs(smartId: String): List<MediaFileInfo> {
        val current = _uiState.value
        val files = current.scan.scannedFiles
        return when (smartId) {
            SMART_FAVORITES -> {
                files.filter { it.uriString in current.favoriteUris }
            }
            SMART_RECENTLY_ADDED -> {
                files.sortedByDescending { it.addedAtMs ?: Long.MIN_VALUE }
            }
            SMART_MOST_PLAYED -> {
                files
                    .mapNotNull { file ->
                        val plays = current.playCounts[file.uriString] ?: 0
                        if (plays > 0) file to plays else null
                    }
                    .sortedWith(
                        compareByDescending<Pair<MediaFileInfo, Int>> { it.second }
                            .thenBy { (it.first.title ?: it.first.displayName).lowercase(Locale.US) }
                    )
                    .map { it.first }
            }
            SMART_NOT_HEARD_RECENTLY -> {
                files.sortedWith(
                    compareBy<MediaFileInfo> { current.lastPlayedAt[it.uriString] != null }
                        .thenBy { current.lastPlayedAt[it.uriString] ?: Long.MIN_VALUE }
                        .thenBy { (it.title ?: it.displayName).lowercase(Locale.US) }
                )
            }
            else -> emptyList()
        }
    }

    private fun refreshSmartPlaylistSelection() {
        val current = _uiState.value
        val selected = current.playlist.selectedPlaylist ?: return
        if (!selected.uriString.startsWith(SMART_PREFIX)) return
        _uiState.value = current.copy(
            playlist = current.playlist.copy(
                playlistSongs = smartPlaylistSongs(selected.uriString)
            )
        )
    }

    private fun persistPlayCounts(playCounts: Map<String, Int>) {
        val encoded = playCounts.entries
            .filter { it.key.isNotBlank() && it.value > 0 }
            .joinToString("\n") { "${it.key}\t${it.value}" }
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAY_COUNTS, encoded)
            .apply()
    }

    private fun persistLastPlayedAt(lastPlayedAt: Map<String, Long>) {
        val encoded = lastPlayedAt.entries
            .filter { it.key.isNotBlank() && it.value > 0L }
            .joinToString("\n") { "${it.key}\t${it.value}" }
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PLAYED_AT, encoded)
            .apply()
    }

    private fun decodePlayCounts(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = mutableMapOf<String, Int>()
        raw.lineSequence().forEach { line ->
            val split = line.split('\t', limit = 2)
            if (split.size != 2) return@forEach
            val uri = split[0].trim()
            val count = split[1].trim().toIntOrNull() ?: return@forEach
            if (uri.isNotEmpty() && count > 0) out[uri] = count
        }
        return out
    }

    private fun decodeLastPlayedAt(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = mutableMapOf<String, Long>()
        raw.lineSequence().forEach { line ->
            val split = line.split('\t', limit = 2)
            if (split.size != 2) return@forEach
            val uri = split[0].trim()
            val value = split[1].trim().toLongOrNull() ?: return@forEach
            if (uri.isNotEmpty() && value > 0L) out[uri] = value
        }
        return out
    }
}
