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

data class ScanState(
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFileInfo> = emptyList(),
    val discoveredPlaylists: List<PlaylistInfo> = emptyList(),
    val scanMessage: String? = null,
    val scanProgress: String? = null,
    val lastScanLimit: Int = MediaCacheService.MAX_CACHE_SIZE,
    val folderMessage: String? = null
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentTrackName: String? = null,
    val currentMediaId: String? = null,
    val isPlayingPlaylist: Boolean = false,
    val queuePosition: String? = null,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false
)

data class LibraryState(
    val selectedTab: LibraryTab = LibraryTab.Songs,
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
    val playlist: PlaylistMgmtState = PlaylistMgmtState()
)

enum class LibraryTab(val label: String) {
    Songs("Songs"),
    Playlists("Playlists"),
    Albums("Albums"),
    Genres("Genres"),
    Artists("Artists"),
    Decades("Decades")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaCacheService = MediaCacheService()
    private val playlistService = PlaylistService()
    private val scanCache =
        mutableMapOf<String, Pair<List<MediaFileInfo>, List<PlaylistInfo>>>()
    private var treeUri: Uri? = null
    private var metadataKey: String? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    override fun onCleared() {
        super.onCleared()
        mediaCacheService.clearCache()
        scanCache.clear()
    }

    internal fun resetAfterScan(
        files: List<MediaFileInfo>,
        playlists: List<PlaylistInfo>,
        maxFiles: Int,
        scanMessage: String? = null,
        scanProgress: String? = null
    ): MainUiState {
        val current = _uiState.value
        return current.copy(
            scan = current.scan.copy(
                isScanning = false,
                scannedFiles = files,
                discoveredPlaylists = playlists,
                lastScanLimit = maxFiles,
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
        forceRescan: Boolean = false
    ) {
        val key = "${treeUri}|$maxFiles"
        if (!forceRescan) {
            val cached = scanCache[key]
            if (cached != null) {
                mediaCacheService.clearCache()
                cached.first.forEach { mediaCacheService.addFile(it) }
                cached.second.forEach { mediaCacheService.addPlaylist(it) }
                _uiState.value = resetAfterScan(cached.first, cached.second, maxFiles)
                metadataKey = null
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!forceRescan) {
                val persisted =
                    mediaCacheService.loadPersistedCache(getApplication(), treeUri, maxFiles)
                if (persisted != null) {
                    scanCache[key] = persisted.files to persisted.playlists
                    _uiState.value = resetAfterScan(persisted.files, persisted.playlists, maxFiles)
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
                maxFiles
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
            mediaCacheService.persistCache(getApplication(), treeUri, maxFiles)
            val seconds = stats.durationMs / 1000.0
            val message = "Scan complete in %.1fs • Folders: %d • Songs: %d".format(
                seconds,
                stats.foldersScanned,
                stats.songsFound
            )
            _uiState.value = resetAfterScan(files, playlists, maxFiles, scanMessage = message)
            metadataKey = null
        }
    }

    fun setTreeUri(uri: Uri) {
        treeUri = uri
        metadataKey = null
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
        val trimmed = query.trim()
        val results = applySearchResults(current.scan.scannedFiles, trimmed)
        _uiState.value = current.copy(
            search = current.search.copy(
                searchQuery = trimmed,
                searchResults = results
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

    fun clearManualPlaylist() {
        val current = _uiState.value
        _uiState.value = current.copy(
            playlist = current.playlist.copy(manualPlaylistSongs = emptyList())
        )
    }

    fun createManualPlaylist(name: String) {
        val uri = treeUri
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
            val updatedPlaylists = current.scan.discoveredPlaylists + result
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
                val updatedPlaylists = current.scan.discoveredPlaylists.filterNot {
                    it.uriString == playlist.uriString
                }
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
        val uri = treeUri ?: return
        val files = _uiState.value.scan.scannedFiles
        if (files.isEmpty()) return
        val safeCount = count.coerceIn(1, files.size)
        val selected = files.shuffled().take(safeCount)
        viewModelScope.launch(Dispatchers.IO) {
            val result = playlistService.writePlaylist(getApplication(), uri, selected)
            val current = _uiState.value
            if (result != null) {
                val updatedPlaylists = current.scan.discoveredPlaylists + result
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

    fun updatePlaybackState(state: Int, mediaId: String?, trackName: String?) {
        val current = _uiState.value
        _uiState.value = current.copy(
            playback = current.playback.copy(
                isPlaying = (state == PlaybackStateCompat.STATE_PLAYING),
                isPaused = (state == PlaybackStateCompat.STATE_PAUSED),
                currentTrackName = if (state == PlaybackStateCompat.STATE_STOPPED) null else trackName,
                currentMediaId = if (state == PlaybackStateCompat.STATE_STOPPED) null else mediaId
            )
        )
    }

    fun updateQueueState(queueTitle: String?, queueSize: Int, activeIndex: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(
            playback = current.playback.copy(
                isPlayingPlaylist = queueTitle != null,
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

    private fun ensureMetadataLoaded() {
        val uriKey = treeUri?.toString() ?: return
        if (metadataKey == uriKey && mediaCacheService.hasAlbumArtistIndexes()) return

        viewModelScope.launch(Dispatchers.IO) {
            val cur = _uiState.value
            _uiState.value = cur.copy(
                library = cur.library.copy(isMetadataLoading = true)
            )
            mediaCacheService.buildAlbumArtistIndexesFromCache()
            val albums = mediaCacheService.albums()
            val artists = mediaCacheService.artists()
            val genres = mediaCacheService.genres()
            val decades = mediaCacheService.decades()
            metadataKey = uriKey
            val cur2 = _uiState.value
            _uiState.value = cur2.copy(
                library = cur2.library.copy(
                    albums = albums,
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
        if (query.isBlank()) return emptyList()
        val needle = query.lowercase()
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
}
