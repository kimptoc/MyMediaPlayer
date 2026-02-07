package com.example.mymediaplayer

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
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
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.mymediaplayer.shared.MyMusicService
import com.example.mymediaplayer.shared.PlaylistInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "mymediaplayer_prefs"
        private const val KEY_TREE_URI = "tree_uri"
        private const val ACTION_SET_MEDIA_FILES = "SET_MEDIA_FILES"
        private const val ACTION_SET_PLAYLISTS = "SET_PLAYLISTS"
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_NAMES = "names"
        private const val EXTRA_SIZES = "sizes"
        private const val EXTRA_PLAYLIST_URIS = "playlist_uris"
        private const val EXTRA_PLAYLIST_NAMES = "playlist_names"
    }

    private val viewModel: MainViewModel by viewModels()

    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null
    private var lastSentUris: List<String>? = null
    private var lastSentPlaylistUris: List<String>? = null

    private var lastPlaybackState: PlaybackStateCompat? = null
    private var lastMetadata: MediaMetadataCompat? = null

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            lastPlaybackState = state
            pushPlaybackState()
            pushQueueState()
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            lastMetadata = metadata
            pushPlaybackState()
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            pushQueueState()
        }

        override fun onQueueTitleChanged(title: CharSequence?) {
            pushQueueState()
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
            pushQueueState()
        }
    }

    private val openDocumentTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TREE_URI, it.toString())
                    .apply()
                viewModel.setTreeUri(it)
                viewModel.onDirectorySelected(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC

        restoreLastTreeUri()

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
                if (state.discoveredPlaylists.isNotEmpty()) {
                    sendPlaylistsToServiceIfNeeded(state.discoveredPlaylists)
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
                    },
                    onNext = {
                        mediaController?.transportControls?.skipToNext()
                    },
                    onCreatePlaylist = { viewModel.createRandomPlaylist() },
                    onPlaylistMessageDismissed = { viewModel.clearPlaylistMessage() },
                    onFolderMessageDismissed = { viewModel.clearFolderMessage() },
                    onTabSelected = { viewModel.selectTab(it) },
                    onAlbumSelected = { viewModel.selectAlbum(it) },
                    onGenreSelected = { viewModel.selectGenre(it) },
                    onArtistSelected = { viewModel.selectArtist(it) },
                    onPlaylistClick = { playlist ->
                        sendFilesToServiceIfNeeded(uiState.value.scannedFiles)
                        sendPlaylistsToServiceIfNeeded(uiState.value.discoveredPlaylists)
                        mediaController?.transportControls?.playFromMediaId(
                            "playlist:${playlist.uriString}",
                            null
                        )
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

    private fun pushQueueState() {
        val controller = mediaController ?: return
        val queueTitle = controller.queueTitle?.toString()
        val queueSize = controller.queue?.size ?: 0
        val activeIndex = lastPlaybackState?.activeQueueItemId?.toInt() ?: -1
        viewModel.updateQueueState(queueTitle, queueSize, activeIndex)
    }

    private fun restoreLastTreeUri() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val uri = Uri.parse(uriString)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPermission) {
            prefs.edit().remove(KEY_TREE_URI).apply()
            viewModel.setFolderMessage("Folder access expired. Please select a folder again.")
            return
        }
        viewModel.setTreeUri(uri)
        viewModel.onDirectorySelected(uri)
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

    private fun sendPlaylistsToServiceIfNeeded(playlists: List<PlaylistInfo>) {
        val controller = mediaController ?: return
        val uris = playlists.map { it.uriString }
        if (uris == lastSentPlaylistUris) return

        val names = playlists.map { it.displayName }
        val bundle = Bundle().apply {
            putStringArrayList(EXTRA_PLAYLIST_URIS, ArrayList(uris))
            putStringArrayList(EXTRA_PLAYLIST_NAMES, ArrayList(names))
        }
        controller.transportControls.sendCustomAction(ACTION_SET_PLAYLISTS, bundle)
        lastSentPlaylistUris = uris
    }
}
