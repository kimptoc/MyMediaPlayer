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

data class MainUiState(
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFileInfo> = emptyList(),
    val discoveredPlaylists: List<PlaylistInfo> = emptyList(),
    val albums: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val decades: List<String> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.Songs,
    val selectedAlbum: String? = null,
    val selectedGenre: String? = null,
    val selectedArtist: String? = null,
    val selectedDecade: String? = null,
    val filteredSongs: List<MediaFileInfo> = emptyList(),
    val isMetadataLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MediaFileInfo> = emptyList(),
    val manualPlaylistSongs: List<MediaFileInfo> = emptyList(),
    val currentTrackName: String? = null,
    val currentMediaId: String? = null,
    val playlistMessage: String? = null,
    val folderMessage: String? = null,
    val scanMessage: String? = null,
    val scanProgress: String? = null,
    val selectedPlaylist: PlaylistInfo? = null,
    val playlistSongs: List<MediaFileInfo> = emptyList(),
    val isPlaylistLoading: Boolean = false,
    val isPlayingPlaylist: Boolean = false,
    val queuePosition: String? = null,
    val lastPlaylistCount: Int = 3,
    val lastScanLimit: Int = MediaCacheService.MAX_CACHE_SIZE,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false
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
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scannedFiles = cached.first,
                    discoveredPlaylists = cached.second,
                    lastScanLimit = maxFiles,
                    lastPlaylistCount = _uiState.value.lastPlaylistCount,
                    selectedPlaylist = null,
                    playlistSongs = emptyList(),
                    isPlaylistLoading = false,
                    albums = emptyList(),
                    genres = emptyList(),
                    artists = emptyList(),
                    decades = emptyList(),
                    selectedAlbum = null,
                    selectedGenre = null,
                    selectedArtist = null,
                    selectedDecade = null,
                    filteredSongs = emptyList(),
                    isMetadataLoading = false,
                    scanMessage = null,
                    scanProgress = null,
                    searchResults = applySearchResults(
                        cached.first,
                        _uiState.value.searchQuery
                    ),
                    manualPlaylistSongs = emptyList()
                )
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
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        scannedFiles = persisted.files,
                        discoveredPlaylists = persisted.playlists,
                        lastScanLimit = maxFiles,
                        lastPlaylistCount = _uiState.value.lastPlaylistCount,
                        selectedPlaylist = null,
                        playlistSongs = emptyList(),
                        isPlaylistLoading = false,
                        albums = emptyList(),
                        genres = emptyList(),
                        artists = emptyList(),
                        decades = emptyList(),
                        selectedAlbum = null,
                        selectedGenre = null,
                        selectedArtist = null,
                        selectedDecade = null,
                        filteredSongs = emptyList(),
                        isMetadataLoading = false,
                        scanMessage = null,
                        scanProgress = null,
                        searchResults = applySearchResults(
                            persisted.files,
                            _uiState.value.searchQuery
                        ),
                        manualPlaylistSongs = emptyList()
                    )
                    metadataKey = null
                    return@launch
                }
            }
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scannedFiles = emptyList(),
                discoveredPlaylists = emptyList(),
                lastScanLimit = maxFiles,
                lastPlaylistCount = _uiState.value.lastPlaylistCount,
                selectedPlaylist = null,
                playlistSongs = emptyList(),
                isPlaylistLoading = false,
                albums = emptyList(),
                genres = emptyList(),
                artists = emptyList(),
                decades = emptyList(),
                selectedAlbum = null,
                selectedGenre = null,
                selectedArtist = null,
                selectedDecade = null,
                filteredSongs = emptyList(),
                isMetadataLoading = false,
                scanMessage = null,
                scanProgress = "Scanning… 0 songs"
            )
            val stats = mediaCacheService.scanDirectory(
                getApplication(),
                treeUri,
                maxFiles
            ) { songsFound, foldersScanned ->
                _uiState.value = _uiState.value.copy(
                    scanProgress = "Scanning… $songsFound songs • $foldersScanned folders",
                    scannedFiles = mediaCacheService.cachedFiles
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
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                scannedFiles = files,
                discoveredPlaylists = playlists,
                lastPlaylistCount = _uiState.value.lastPlaylistCount,
                lastScanLimit = maxFiles,
                selectedPlaylist = null,
                playlistSongs = emptyList(),
                isPlaylistLoading = false,
                scanMessage = message,
                scanProgress = null,
                searchResults = applySearchResults(
                    files,
                    _uiState.value.searchQuery
                ),
                manualPlaylistSongs = emptyList()
            )
            metadataKey = null
        }
    }

    fun setTreeUri(uri: Uri) {
        treeUri = uri
        metadataKey = null
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            selectedAlbum = if (tab == LibraryTab.Albums) _uiState.value.selectedAlbum else null,
            selectedGenre = if (tab == LibraryTab.Genres) _uiState.value.selectedGenre else null,
            selectedArtist = if (tab == LibraryTab.Artists) _uiState.value.selectedArtist else null,
            selectedDecade = if (tab == LibraryTab.Decades) _uiState.value.selectedDecade else null,
            filteredSongs = if (
                tab == LibraryTab.Albums ||
                tab == LibraryTab.Genres ||
                tab == LibraryTab.Artists ||
                tab == LibraryTab.Decades
            ) {
                _uiState.value.filteredSongs
            } else {
                emptyList()
            }
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
        _uiState.value = _uiState.value.copy(
            selectedAlbum = album,
            selectedGenre = null,
            selectedArtist = null
        )
        applyFilteredSongs()
    }

    fun selectGenre(genre: String) {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            selectedGenre = genre,
            selectedArtist = null
        )
        applyFilteredSongs()
    }

    fun selectArtist(artist: String) {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            selectedGenre = null,
            selectedArtist = artist,
            selectedDecade = null
        )
        applyFilteredSongs()
    }

    fun selectDecade(decade: String) {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            selectedGenre = null,
            selectedArtist = null,
            selectedDecade = decade
        )
        applyFilteredSongs()
    }

    fun clearCategorySelection() {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            selectedGenre = null,
            selectedArtist = null,
            selectedDecade = null,
            filteredSongs = emptyList()
        )
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value
        val trimmed = query.trim()
        val results = applySearchResults(current.scannedFiles, trimmed)
        _uiState.value = current.copy(
            searchQuery = trimmed,
            searchResults = results
        )
    }

    fun addToManualPlaylist(file: MediaFileInfo) {
        val current = _uiState.value
        if (current.manualPlaylistSongs.any { it.uriString == file.uriString }) return
        _uiState.value = current.copy(
            manualPlaylistSongs = current.manualPlaylistSongs + file
        )
    }

    fun clearManualPlaylist() {
        _uiState.value = _uiState.value.copy(manualPlaylistSongs = emptyList())
    }

    fun createManualPlaylist(name: String) {
        val uri = treeUri
        val current = _uiState.value
        if (uri == null) {
            _uiState.value = current.copy(playlistMessage = "Select a folder first.")
            return
        }
        if (current.manualPlaylistSongs.isEmpty()) {
            _uiState.value = current.copy(playlistMessage = "Add songs first.")
            return
        }
        val result = playlistService.writePlaylistWithName(
            getApplication(),
            uri,
            current.manualPlaylistSongs,
            name
        )
        if (result != null) {
            val updatedPlaylists = current.discoveredPlaylists + result
            val key = treeUri?.let { "${it}|${current.lastScanLimit}" }
            if (key != null) {
                val cached = scanCache[key]
                if (cached != null) {
                    scanCache[key] = cached.first to (cached.second + result)
                }
            }
            mediaCacheService.addPlaylist(result)
            _uiState.value = current.copy(
                discoveredPlaylists = updatedPlaylists,
                playlistMessage = "Created ${result.displayName}",
                manualPlaylistSongs = emptyList()
            )
            treeUri?.let { tree ->
                val limit = current.lastScanLimit
                viewModelScope.launch(Dispatchers.IO) {
                    mediaCacheService.persistCache(getApplication(), tree, limit)
                }
            }
        } else {
            _uiState.value = current.copy(playlistMessage = "Failed to create playlist")
        }
    }

    fun addSongToExistingPlaylist(playlist: PlaylistInfo, file: MediaFileInfo) {
        val uri = Uri.parse(playlist.uriString)
        val success = playlistService.appendToPlaylist(getApplication(), uri, listOf(file))
        val current = _uiState.value
        if (success) {
            val updatedSongs = if (current.selectedPlaylist?.uriString == playlist.uriString) {
                current.playlistSongs + file
            } else {
                current.playlistSongs
            }
            _uiState.value = current.copy(
                playlistSongs = updatedSongs,
                playlistMessage = "Added to ${playlist.displayName.removeSuffix(".m3u")}"
            )
        } else {
            _uiState.value = current.copy(playlistMessage = "Failed to update playlist")
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
                val updatedPlaylists = current.discoveredPlaylists.filterNot {
                    it.uriString == playlist.uriString
                }
                val key = treeUri?.let { "${it}|${current.lastScanLimit}" }
                if (key != null) {
                    val cached = scanCache[key]
                    if (cached != null) {
                        scanCache[key] = cached.first to updatedPlaylists
                    }
                }
                mediaCacheService.removePlaylistByUri(playlist.uriString)
                _uiState.value = current.copy(
                    discoveredPlaylists = updatedPlaylists,
                    selectedPlaylist = if (current.selectedPlaylist?.uriString == playlist.uriString) {
                        null
                    } else {
                        current.selectedPlaylist
                    },
                    playlistSongs = if (current.selectedPlaylist?.uriString == playlist.uriString) {
                        emptyList()
                    } else {
                        current.playlistSongs
                    },
                    playlistMessage = "Deleted ${playlist.displayName.removeSuffix(".m3u")}"
                )
                treeUri?.let { tree ->
                    val limit = current.lastScanLimit
                    mediaCacheService.persistCache(getApplication(), tree, limit)
                }
            } else {
                _uiState.value = current.copy(playlistMessage = "Failed to delete playlist")
            }
        }
    }

    fun selectPlaylist(playlist: PlaylistInfo) {
        _uiState.value = _uiState.value.copy(
            selectedPlaylist = playlist,
            playlistSongs = emptyList(),
            isPlaylistLoading = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            val songs = playlistService.readPlaylist(getApplication(), Uri.parse(playlist.uriString))
            val byUri = _uiState.value.scannedFiles.associateBy { it.uriString }
            val enriched = songs.map { song -> byUri[song.uriString] ?: song }
            _uiState.value = _uiState.value.copy(
                playlistSongs = enriched,
                isPlaylistLoading = false
            )
        }
    }

    fun clearSelectedPlaylist() {
        _uiState.value = _uiState.value.copy(
            selectedPlaylist = null,
            playlistSongs = emptyList(),
            isPlaylistLoading = false
        )
    }

    fun createRandomPlaylist(count: Int) {
        val uri = treeUri ?: return
        val files = _uiState.value.scannedFiles
        if (files.isEmpty()) return
        val safeCount = count.coerceIn(1, files.size)
        val selected = files.shuffled().take(safeCount)
        viewModelScope.launch(Dispatchers.IO) {
            val result = playlistService.writePlaylist(getApplication(), uri, selected)
            if (result != null) {
                val updatedPlaylists = _uiState.value.discoveredPlaylists + result
                _uiState.value = _uiState.value.copy(
                    discoveredPlaylists = updatedPlaylists,
                    playlistMessage = "Created ${result.displayName}",
                    lastPlaylistCount = safeCount
                )
                val key = uri.toString()
                val existing = scanCache[key]
                if (existing != null) {
                    scanCache[key] = existing.first to updatedPlaylists
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    playlistMessage = "Failed to create playlist",
                    lastPlaylistCount = safeCount
                )
            }
        }
    }

    fun clearPlaylistMessage() {
        _uiState.value = _uiState.value.copy(playlistMessage = null)
    }

    fun setFolderMessage(message: String) {
        _uiState.value = _uiState.value.copy(folderMessage = message)
    }

    fun clearFolderMessage() {
        _uiState.value = _uiState.value.copy(folderMessage = null)
    }

    fun clearScanMessage() {
        _uiState.value = _uiState.value.copy(scanMessage = null)
    }

    fun updatePlaybackState(state: Int, mediaId: String?, trackName: String?) {
        val current = _uiState.value
        _uiState.value = current.copy(
            isPlaying = (state == PlaybackStateCompat.STATE_PLAYING),
            isPaused = (state == PlaybackStateCompat.STATE_PAUSED),
            currentTrackName = if (state == PlaybackStateCompat.STATE_STOPPED) null else trackName,
            currentMediaId = if (state == PlaybackStateCompat.STATE_STOPPED) null else mediaId
        )
    }

    fun updateQueueState(queueTitle: String?, queueSize: Int, activeIndex: Int) {
        _uiState.value = _uiState.value.copy(
            isPlayingPlaylist = queueTitle != null,
            queuePosition = if (queueTitle != null && queueSize > 0 && activeIndex >= 0) {
                "${activeIndex + 1}/$queueSize"
            } else {
                null
            },
            hasPrev = queueTitle != null && activeIndex > 0,
            hasNext = queueTitle != null && activeIndex >= 0 && activeIndex < queueSize - 1
        )
    }

    private fun ensureMetadataLoaded() {
        val uriKey = treeUri?.toString() ?: return
        if (metadataKey == uriKey && mediaCacheService.hasAlbumArtistIndexes()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isMetadataLoading = true)
            mediaCacheService.buildAlbumArtistIndexesFromCache()
            val albums = mediaCacheService.albums()
            val artists = mediaCacheService.artists()
            val genres = mediaCacheService.genres()
            val decades = mediaCacheService.decades()
            metadataKey = uriKey
            _uiState.value = _uiState.value.copy(
                albums = albums,
                genres = genres,
                artists = artists,
                decades = decades,
                isMetadataLoading = false
            )
            applyFilteredSongs()
        }
    }

    private fun applyFilteredSongs() {
        val current = _uiState.value
        val filtered = when {
            current.selectedAlbum != null ->
                mediaCacheService.songsForAlbum(current.selectedAlbum)
            current.selectedGenre != null ->
                mediaCacheService.songsForGenre(current.selectedGenre)
            current.selectedArtist != null ->
                mediaCacheService.songsForArtist(current.selectedArtist)
            current.selectedDecade != null ->
                mediaCacheService.songsForDecade(current.selectedDecade)
            else -> emptyList()
        }
        _uiState.value = current.copy(filteredSongs = filtered)
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
