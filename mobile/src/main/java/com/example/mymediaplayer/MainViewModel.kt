package com.example.mymediaplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymediaplayer.shared.MediaCacheService
import com.example.mymediaplayer.shared.MediaFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFileInfo> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaCacheService = MediaCacheService()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun onDirectorySelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = MainUiState(isScanning = true)
            mediaCacheService.scanDirectory(getApplication(), treeUri)
            _uiState.value = MainUiState(
                isScanning = false,
                scannedFiles = mediaCacheService.cachedFiles
            )
        }
    }
}
