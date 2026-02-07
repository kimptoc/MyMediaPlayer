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
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentTrackName: String? = null,
    val currentMediaId: String? = null,
    val playlistMessage: String? = null,
    val folderMessage: String? = null,
    val isPlayingPlaylist: Boolean = false,
    val queuePosition: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaCacheService = MediaCacheService()
    private val playlistService = PlaylistService()
    private val scanCache =
        mutableMapOf<String, Pair<List<MediaFileInfo>, List<PlaylistInfo>>>()
    private var treeUri: Uri? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun onDirectorySelected(treeUri: Uri, forceRescan: Boolean = false) {
        val key = treeUri.toString()
        if (!forceRescan) {
            val cached = scanCache[key]
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scannedFiles = cached.first,
                    discoveredPlaylists = cached.second
                )
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scannedFiles = emptyList(),
                discoveredPlaylists = emptyList()
            )
            mediaCacheService.scanDirectory(getApplication(), treeUri)
            val files = mediaCacheService.cachedFiles
            val playlists = mediaCacheService.discoveredPlaylists
            scanCache[key] = files to playlists
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                scannedFiles = files,
                discoveredPlaylists = playlists
            )
        }
    }

    fun setTreeUri(uri: Uri) {
        treeUri = uri
    }

    fun createRandomPlaylist() {
        val uri = treeUri ?: return
        val files = _uiState.value.scannedFiles
        if (files.isEmpty()) return
        val selected = files.shuffled().take(3)
        viewModelScope.launch(Dispatchers.IO) {
            val result = playlistService.writePlaylist(getApplication(), uri, selected)
            _uiState.value = _uiState.value.copy(
                playlistMessage = if (result != null) {
                    "Created $result"
                } else {
                    "Failed to create playlist"
                }
            )
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
            }
        )
    }
}
