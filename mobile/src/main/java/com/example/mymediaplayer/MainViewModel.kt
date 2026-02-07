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
    val selectedTab: LibraryTab = LibraryTab.Songs,
    val selectedAlbum: String? = null,
    val selectedGenre: String? = null,
    val selectedArtist: String? = null,
    val filteredSongs: List<MediaFileInfo> = emptyList(),
    val isMetadataLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentTrackName: String? = null,
    val currentMediaId: String? = null,
    val playlistMessage: String? = null,
    val folderMessage: String? = null,
    val scanMessage: String? = null,
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
    Artists("Artists")
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
                    selectedAlbum = null,
                    selectedGenre = null,
                    selectedArtist = null,
                    filteredSongs = emptyList(),
                    isMetadataLoading = false,
                    scanMessage = null
                )
                metadataKey = null
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
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
                selectedAlbum = null,
                selectedGenre = null,
                selectedArtist = null,
                filteredSongs = emptyList(),
                isMetadataLoading = false,
                scanMessage = null
            )
            val stats = mediaCacheService.scanDirectory(getApplication(), treeUri, maxFiles)
            val files = mediaCacheService.cachedFiles
            val playlists = mediaCacheService.discoveredPlaylists
            scanCache[key] = files to playlists
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
                scanMessage = message
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
            filteredSongs = if (tab == LibraryTab.Albums || tab == LibraryTab.Genres || tab == LibraryTab.Artists) {
                _uiState.value.filteredSongs
            } else {
                emptyList()
            }
        )
        if (tab == LibraryTab.Albums || tab == LibraryTab.Genres || tab == LibraryTab.Artists) {
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
            selectedArtist = artist
        )
        applyFilteredSongs()
    }

    fun clearCategorySelection() {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            selectedGenre = null,
            selectedArtist = null,
            filteredSongs = emptyList()
        )
    }

    fun selectPlaylist(playlist: PlaylistInfo) {
        _uiState.value = _uiState.value.copy(
            selectedPlaylist = playlist,
            playlistSongs = emptyList(),
            isPlaylistLoading = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            val songs = playlistService.readPlaylist(getApplication(), Uri.parse(playlist.uriString))
            _uiState.value = _uiState.value.copy(
                playlistSongs = songs,
                isPlaylistLoading = false
            )
        }
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
        if (metadataKey == uriKey && mediaCacheService.hasMetadataIndexes()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isMetadataLoading = true)
            mediaCacheService.buildMetadataIndexes(getApplication())
            val albums = mediaCacheService.albums()
            val genres = mediaCacheService.genres()
            val artists = mediaCacheService.artists()
            metadataKey = uriKey
            _uiState.value = _uiState.value.copy(
                albums = albums,
                genres = genres,
                artists = artists,
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
            else -> emptyList()
        }
        _uiState.value = current.copy(filteredSongs = filtered)
    }
}
