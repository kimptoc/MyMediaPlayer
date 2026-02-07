package com.example.mymediaplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymediaplayer.shared.MediaCacheService
import com.example.mymediaplayer.shared.MediaFileInfo
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFileInfo> = emptyList(),
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentTrackName: String? = null,
    val currentMediaId: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaCacheService = MediaCacheService()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun onDirectorySelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scannedFiles = emptyList()
            )
            mediaCacheService.scanDirectory(getApplication(), treeUri)
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                scannedFiles = mediaCacheService.cachedFiles
            )
        }
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
}
