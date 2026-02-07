package com.example.mymediaplayer

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.mymediaplayer.shared.MyMusicService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_SET_MEDIA_FILES = "SET_MEDIA_FILES"
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_NAMES = "names"
        private const val EXTRA_SIZES = "sizes"
    }

    private val viewModel: MainViewModel by viewModels()

    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null
    private var lastSentUris: List<String>? = null

    private var lastPlaybackState: PlaybackStateCompat? = null
    private var lastMetadata: MediaMetadataCompat? = null

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            lastPlaybackState = state
            pushPlaybackState()
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            lastMetadata = metadata
            pushPlaybackState()
        }
    }

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val browser = mediaBrowser ?: return
            val controller = MediaControllerCompat(this@MainActivity, browser.sessionToken)
            mediaController = controller
            MediaControllerCompat.setMediaController(this@MainActivity, controller)
            controller.registerCallback(controllerCallback)
            lastPlaybackState = controller.playbackState
            lastMetadata = controller.metadata
            pushPlaybackState()
        }
    }

    private val openDocumentTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.onDirectorySelected(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MyMusicService::class.java),
            connectionCallback,
            null
        ).apply { connect() }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.scannedFiles.isNotEmpty()) {
                    sendFilesToServiceIfNeeded(state.scannedFiles)
                }
            }
        }

        setContent {
            MaterialTheme {
                val uiState = viewModel.uiState.collectAsState()
                MainScreen(
                    uiState = uiState.value,
                    onSelectFolder = { openDocumentTree.launch(null) },
                    onFileClick = { file ->
                        sendFilesToServiceIfNeeded(uiState.value.scannedFiles)
                        mediaController?.transportControls?.playFromMediaId(file.uriString, null)
                    },
                    onPlayPause = {
                        val isPlaying = uiState.value.isPlaying
                        if (isPlaying) {
                            mediaController?.transportControls?.pause()
                        } else {
                            mediaController?.transportControls?.play()
                        }
                    },
                    onStop = {
                        mediaController?.transportControls?.stop()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser?.disconnect()
        super.onDestroy()
    }

    private fun pushPlaybackState() {
        val state = lastPlaybackState?.state ?: PlaybackStateCompat.STATE_NONE
        val mediaId = lastMetadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        val trackName = lastMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        viewModel.updatePlaybackState(state, mediaId, trackName)
    }

    private fun sendFilesToServiceIfNeeded(files: List<com.example.mymediaplayer.shared.MediaFileInfo>) {
        val controller = mediaController ?: return
        val uris = files.map { it.uriString }
        if (uris == lastSentUris) return

        val names = files.map { it.displayName }
        val sizes = files.map { it.sizeBytes }.toLongArray()

        val bundle = Bundle().apply {
            putStringArrayList(EXTRA_URIS, ArrayList(uris))
            putStringArrayList(EXTRA_NAMES, ArrayList(names))
            putLongArray(EXTRA_SIZES, sizes)
        }
        controller.transportControls.sendCustomAction(ACTION_SET_MEDIA_FILES, bundle)
        lastSentUris = uris
    }
}
