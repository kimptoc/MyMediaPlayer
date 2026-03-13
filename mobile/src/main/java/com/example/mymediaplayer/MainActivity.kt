package com.example.mymediaplayer

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.Manifest
import android.app.SearchManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.graphics.Bitmap
import android.widget.Toast
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.mymediaplayer.shared.ApiKeyStore
import com.example.mymediaplayer.shared.MediaCacheService
import com.example.mymediaplayer.shared.MyMusicService
import com.example.mymediaplayer.shared.PlaylistInfo
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "mymediaplayer_prefs"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_PLAYLIST_TREE_URI = "playlist_tree_uri"
        private const val KEY_SCAN_LIMIT = "scan_limit"
        private const val KEY_SCAN_DEEP = "scan_deep"
        private const val KEY_SCAN_WHOLE_DRIVE = "scan_whole_drive"
        private const val KEY_NOTIF_PROMPTED = "notif_prompted"
        private const val KEY_NOTIF_PROMPT_STATE = "notif_prompt_state"
        private const val NOTIF_PROMPT_UNKNOWN = 0
        private const val NOTIF_PROMPT_REQUESTED = 1
        private const val NOTIF_PROMPT_GRANTED = 2
        private const val NOTIF_PROMPT_DENIED = 3
        private const val KEY_TRACK_VOICE_INTRO_ENABLED = "track_voice_intro_enabled"
        private const val KEY_TRACK_VOICE_OUTRO_ENABLED = "track_voice_outro_enabled"
        private const val KEY_DEBUG_CLOUD_ANNOUNCEMENTS = "debug_cloud_announcements"
        private const val KEY_BT_AUTOPLAY_ENABLED = "bt_autoplay_enabled"
        private const val KEY_BT_AUTOPLAY_ADDRESSES = "bt_autoplay_addresses"
        private const val KEY_BT_AUTOPLAY_DEVICES = "bt_autoplay_devices"
        private const val KEY_BT_LAST_AUTOPLAY_MS = "bt_last_autoplay_ms"
        private const val KEY_BT_LAST_EVENT_MS = "bt_last_event_ms"
        private const val KEY_BT_LAST_REASON = "bt_last_reason"
        private const val KEY_BT_LAST_DEVICE = "bt_last_device"
        private const val KEY_BT_LAST_DEVICE_NAME = "bt_last_device_name"
        private const val ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"
        private const val ACTION_SET_MEDIA_FILES = "SET_MEDIA_FILES"
        private const val ACTION_REFRESH_LIBRARY = "REFRESH_LIBRARY"
        private const val ACTION_SET_PLAYLISTS = "SET_PLAYLISTS"
        private const val ACTION_PLAY_SEARCH_LIST = "PLAY_SEARCH_LIST"
        private const val ACTION_PLAY_UI_LIST = "PLAY_UI_LIST"
        private const val ACTION_SHUFFLE_PREFIX = "action:shuffle:"
        private const val SMART_PLAYLIST_PREFIX = "smart_playlist:"
        private const val PLAYLIST_URI_PREFIX = "playlist_uri:"
        private const val ACTION_SET_TRACK_VOICE_INTRO = "SET_TRACK_VOICE_INTRO"
        private const val ACTION_SET_TRACK_VOICE_OUTRO = "SET_TRACK_VOICE_OUTRO"
        private const val ACTION_SET_DEBUG_CLOUD = "SET_DEBUG_CLOUD"
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_NAMES = "names"
        private const val EXTRA_SIZES = "sizes"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_ARTISTS = "artists"
        private const val EXTRA_ALBUMS = "albums"
        private const val EXTRA_GENRES = "genres"
        private const val EXTRA_DURATIONS = "durations"
        private const val EXTRA_YEARS = "years"
        private const val EXTRA_ADDED_AT = "added_at"
        private const val EXTRA_PLAYLIST_URIS = "playlist_uris"
        private const val EXTRA_PLAYLIST_NAMES = "playlist_names"
        private const val EXTRA_SEARCH_URIS = "search_uris"
        private const val EXTRA_SEARCH_SHUFFLE = "search_shuffle"
        private const val EXTRA_LIST_URIS = "list_uris"
        private const val EXTRA_LIST_SHUFFLE = "list_shuffle"
        private const val EXTRA_LIST_TITLE = "list_title"
        private const val EXTRA_TRACK_VOICE_INTRO_ENABLED = "track_voice_intro_enabled"
        private const val EXTRA_TRACK_VOICE_OUTRO_ENABLED = "track_voice_outro_enabled"
        private const val MAX_MEDIA_FILES_FOR_BUNDLE = 500
    }

    private val viewModel: MainViewModel by viewModels()

    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null
    private var lastSentUris: List<String>? = null
    private var lastSentLargeLibraryCount: Int? = null
    private var lastSentPlaylistUris: List<String>? = null
    private var pendingScanLimit: Int = MediaCacheService.MAX_CACHE_SIZE
    private var pendingDeepScan: Boolean = false
    private var pendingWholeDriveScanLimit: Int? = null

    private var lastPlaybackState: PlaybackStateCompat? = null
    private var lastMetadata: MediaMetadataCompat? = null
    private var lastRepeatMode: Int = 0
    private var bluetoothAutoPlayEnabled by mutableStateOf(false)
    private var trustedBluetoothDevices by mutableStateOf<List<TrustedBluetoothDevice>>(emptyList())
    private var bluetoothDiagnostics by mutableStateOf("No Bluetooth auto-play events yet")
    private var pendingVoiceSearchQuery: String? = null
    private var pendingVoiceSearchExtras: Bundle? = null
    private var showPlaylistSaveFolderPrompt by mutableStateOf(false)
    private var trackVoiceIntroEnabled by mutableStateOf(false)
    private var trackVoiceOutroEnabled by mutableStateOf(false)
    private var cloudAnnouncementKiloKey by mutableStateOf("")
    private var cloudAnnouncementTtsKey by mutableStateOf("")
    private var debugCloudAnnouncements by mutableStateOf(false)
    private var nowPlayingArt by mutableStateOf<Bitmap?>(null)

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Bluetooth permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            lastPlaybackState = state
            pushPlaybackState()
            pushQueueState()
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            lastMetadata = metadata
            nowPlayingArt =
                metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            pushPlaybackState()
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            pushQueueState()
        }

        override fun onQueueTitleChanged(title: CharSequence?) {
            pushQueueState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            lastRepeatMode = repeatMode
            viewModel.updateRepeatMode(repeatMode)
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
            lastRepeatMode = controller.repeatMode
            pushPlaybackState()
            pushQueueState()
            viewModel.updateRepeatMode(lastRepeatMode)
            sendTrackVoiceIntroSettingToService()
            sendTrackVoiceOutroSettingToService()
            dispatchPendingVoiceSearchIfNeeded()
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
                    .putInt(KEY_SCAN_LIMIT, pendingScanLimit)
                    .putBoolean(KEY_SCAN_DEEP, pendingDeepScan)
                    .putBoolean(KEY_SCAN_WHOLE_DRIVE, false)
                    .apply()
                viewModel.setTreeUri(it)
                viewModel.onDirectorySelected(it, pendingScanLimit, deepScan = pendingDeepScan)
            }
        }

    private val openPlaylistDocumentTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PLAYLIST_TREE_URI, it.toString())
                    .commit()
                viewModel.setPlaylistTreeUri(it)
                showPlaylistSaveFolderPrompt = false
                Toast.makeText(this, "Playlist save folder updated", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NOTIF_PROMPTED, true)
                .putInt(
                    KEY_NOTIF_PROMPT_STATE,
                    if (granted) NOTIF_PROMPT_GRANTED else NOTIF_PROMPT_DENIED
                )
                .commit()
        }

    private val requestMediaReadPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val limit = pendingWholeDriveScanLimit
            pendingWholeDriveScanLimit = null
            if (granted && limit != null) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_SCAN_LIMIT, limit)
                    .putBoolean(KEY_SCAN_WHOLE_DRIVE, true)
                    .apply()
                viewModel.scanWholeDevice(limit)
            } else if (!granted) {
                Toast.makeText(this, "Media permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        bluetoothAutoPlayEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_BT_AUTOPLAY_ENABLED, false)
        trackVoiceIntroEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_TRACK_VOICE_INTRO_ENABLED, false)
        trackVoiceOutroEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_TRACK_VOICE_OUTRO_ENABLED, false)
        debugCloudAnnouncements = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_DEBUG_CLOUD_ANNOUNCEMENTS, false)
        val encryptedPrefs = com.example.mymediaplayer.shared.ApiKeyStore.getPrefs(this)
        cloudAnnouncementKiloKey = encryptedPrefs
            ?.getString(com.example.mymediaplayer.shared.ApiKeyStore.KEY_KILO, "") ?: ""
        cloudAnnouncementTtsKey = encryptedPrefs
            ?.getString(com.example.mymediaplayer.shared.ApiKeyStore.KEY_CLOUD_TTS, "") ?: ""
        refreshBluetoothState()
        maybeRequestPostNotifications()

        restoreLastTreeUri()
        showPlaylistSaveFolderPrompt = !hasValidPlaylistSaveFolder()

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MyMusicService::class.java),
            connectionCallback,
            null
        ).apply { connect() }
        handleIncomingIntent(intent)

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.scan.scannedFiles.isNotEmpty()) {
                    sendFilesToServiceIfNeeded(state.scan.scannedFiles)
                }
                if (state.scan.discoveredPlaylists.isNotEmpty()) {
                    sendPlaylistsToServiceIfNeeded(state.scan.discoveredPlaylists)
                }
            }
        }

        setContent {
            MaterialTheme {
                val uiState = viewModel.uiState.collectAsState()
                MainScreen(
                    uiState = uiState.value,
                    onSelectFolderWithLimit = { limit, deepScan ->
                        pendingScanLimit = limit
                        pendingDeepScan = deepScan
                        openDocumentTree.launch(null)
                    },
                    onChoosePlaylistSaveFolder = {
                        openPlaylistDocumentTree.launch(null)
                    },
                    onScanWholeDriveWithLimit = { limit ->
                        if (hasMediaReadPermission()) {
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putInt(KEY_SCAN_LIMIT, limit)
                                .putBoolean(KEY_SCAN_WHOLE_DRIVE, true)
                                .apply()
                            viewModel.scanWholeDevice(limit)
                        } else {
                            pendingWholeDriveScanLimit = limit
                            requestMediaReadPermission.launch(requiredMediaReadPermission())
                        }
                    },
                    onFileClick = { file ->
                        sendFilesToServiceIfNeeded(uiState.value.scan.scannedFiles)
                        mediaController?.transportControls?.playFromMediaId(file.uriString, null)
                    },
                    onPlayPause = {
                        val isPlaying = uiState.value.playback.isPlaying
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
                    onPrev = {
                        mediaController?.transportControls?.skipToPrevious()
                    },
                    onToggleRepeat = {
                        val current = uiState.value.playback.repeatMode
                        val next = when (current) {
                            PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                            PlaybackStateCompat.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_NONE
                            else -> PlaybackStateCompat.REPEAT_MODE_ALL
                        }
                        mediaController?.transportControls?.setRepeatMode(next)
                    },
                    onQueueItemSelected = { queueId ->
                        mediaController?.transportControls?.skipToQueueItem(queueId)
                    },
                    onSeekTo = { positionMs ->
                        mediaController?.transportControls?.seekTo(positionMs)
                    },
                    onCreatePlaylist = { count -> viewModel.createRandomPlaylist(count) },
                    onPlaylistMessageDismissed = { viewModel.clearPlaylistMessage() },
                    onFolderMessageDismissed = { viewModel.clearFolderMessage() },
                    onScanMessageDismissed = { viewModel.clearScanMessage() },
                    onTabSelected = { viewModel.selectTab(it) },
                    onAlbumSelected = { viewModel.selectAlbum(it) },
                    onAlbumSortModeChanged = { viewModel.setAlbumSortMode(it) },
                    onGenreSelected = { viewModel.selectGenre(it) },
                    onArtistSelected = { viewModel.selectArtist(it) },
                    onDecadeSelected = { viewModel.selectDecade(it) },
                    onSearchQueryChanged = { viewModel.updateSearchQuery(it) },
                    onClearSearch = { viewModel.clearSearch() },
                    onClearCategorySelection = { viewModel.clearCategorySelection() },
                    onPlaylistSelected = { viewModel.selectPlaylist(it) },
                    onClearPlaylistSelection = { viewModel.clearSelectedPlaylist() },
                    onDeletePlaylist = { playlist -> viewModel.deletePlaylist(playlist) },
                    onRenamePlaylist = { playlist, newName ->
                        viewModel.renamePlaylist(playlist, newName)
                    },
                    onSavePlaylistEdits = { playlist, songs ->
                        viewModel.savePlaylistEdits(playlist, songs)
                    },
                    onPlaySongs = { songs ->
                        playUiList(
                            songs = songs,
                            shuffle = false,
                            queueTitle = queueTitleForCurrentUiList(uiState.value)
                        )
                    },
                    onShuffleSongs = { songs ->
                        playUiList(
                            songs = songs,
                            shuffle = true,
                            queueTitle = queueTitleForCurrentUiList(uiState.value)
                        )
                    },
                    onPlaySearchResults = { songs ->
                        playSearchResults(songs, shuffle = false)
                    },
                    onShuffleSearchResults = { songs ->
                        playSearchResults(songs, shuffle = true)
                    },
                    onAddToExistingPlaylist = { playlist, files ->
                        viewModel.addManyToExistingPlaylist(playlist, files)
                    },
                    onCreatePlaylistFromSongs = { name, files ->
                        viewModel.createPlaylistFromSongs(name, files)
                    },
                    onToggleFavorite = { file ->
                        viewModel.toggleFavorite(file.uriString)
                    },
                    bluetoothAutoPlayEnabled = bluetoothAutoPlayEnabled,
                    trackVoiceIntroEnabled = trackVoiceIntroEnabled,
                    trackVoiceOutroEnabled = trackVoiceOutroEnabled,
                    onToggleBluetoothAutoPlay = {
                        toggleBluetoothAutoPlay()
                    },
                    onToggleTrackVoiceIntro = {
                        toggleTrackVoiceIntro()
                    },
                    onToggleTrackVoiceOutro = {
                        toggleTrackVoiceOutro()
                    },
                    onAddCurrentBluetoothDevice = {
                        addCurrentBluetoothDeviceToAllowlist()
                    },
                    trustedBluetoothDevices = trustedBluetoothDevices,
                    bluetoothDiagnostics = bluetoothDiagnostics,
                    onRemoveTrustedBluetoothDevice = { address ->
                        removeTrustedBluetoothDevice(address)
                    },
                    onClearTrustedBluetoothDevices = {
                        clearTrustedBluetoothDevices()
                    },
                    onRefreshBluetoothDiagnostics = {
                        refreshBluetoothState()
                    },
                    nowPlayingArt = nowPlayingArt,
                    showPlaylistSaveFolderPrompt = showPlaylistSaveFolderPrompt,
                    onDismissPlaylistSaveFolderPrompt = {
                        showPlaylistSaveFolderPrompt = false
                    },
                    onSetPlaylistSaveFolderNow = {
                        showPlaylistSaveFolderPrompt = false
                        openPlaylistDocumentTree.launch(null)
                    },
                    cloudAnnouncementKiloKey = cloudAnnouncementKiloKey,
                    cloudAnnouncementTtsKey = cloudAnnouncementTtsKey,
                    onSaveCloudAnnouncementKeys = { kilo, tts, onValidated ->
                        cloudAnnouncementKiloKey = kilo
                        cloudAnnouncementTtsKey = tts
                        ApiKeyStore.getPrefs(this)
                            ?.edit()
                            ?.putString(ApiKeyStore.KEY_KILO, kilo)
                            ?.putString(ApiKeyStore.KEY_CLOUD_TTS, tts)
                            ?.apply()
                        lifecycleScope.launch {
                            val (kiloResult, ttsResult) = ApiKeyStore.validateKeys(this@MainActivity)
                            val kiloMsg = when (kiloResult) {
                                is ApiKeyStore.ValidationResult.Success -> "Kilo API: OK"
                                is ApiKeyStore.ValidationResult.Error -> "Kilo API: ${kiloResult.message}"
                            }
                            val ttsMsg = when (ttsResult) {
                                is ApiKeyStore.ValidationResult.Success -> "Google TTS: OK"
                                is ApiKeyStore.ValidationResult.Error -> "Google TTS: Not configured (using on-device)"
                            }
                            Toast.makeText(this@MainActivity, "$kiloMsg\n$ttsMsg", Toast.LENGTH_LONG).show()
                            onValidated()
                        }
                    },
                    debugCloudAnnouncements = debugCloudAnnouncements,
                    onSetDebugCloudAnnouncements = { enabled ->
                        debugCloudAnnouncements = enabled
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_DEBUG_CLOUD_ANNOUNCEMENTS, enabled)
                            .apply()
                        sendDebugCloudSettingToService(enabled)
                    },
                    onPlayPlaylist = { playlist ->
                        sendFilesToServiceIfNeeded(uiState.value.scan.scannedFiles)
                        sendPlaylistsToServiceIfNeeded(uiState.value.scan.discoveredPlaylists)
                        val mediaId = if (playlist.uriString.startsWith(MainViewModel.SMART_PREFIX)) {
                            val smartId = playlist.uriString.removePrefix(MainViewModel.SMART_PREFIX)
                            SMART_PLAYLIST_PREFIX + Uri.encode(smartId)
                        } else {
                            "playlist:${playlist.uriString}"
                        }
                        mediaController?.transportControls?.playFromMediaId(mediaId, null)
                    },
                    onShufflePlaylistSongs = { playlist, songs ->
                        if (songs.isNotEmpty()) {
                            sendFilesToServiceIfNeeded(uiState.value.scan.scannedFiles)
                            sendPlaylistsToServiceIfNeeded(uiState.value.scan.discoveredPlaylists)
                            val listKey = if (playlist.uriString.startsWith(MainViewModel.SMART_PREFIX)) {
                                val smartId = playlist.uriString.removePrefix(MainViewModel.SMART_PREFIX)
                                SMART_PLAYLIST_PREFIX + Uri.encode(smartId)
                            } else {
                                PLAYLIST_URI_PREFIX + Uri.encode(playlist.uriString)
                            }
                            mediaController?.transportControls?.playFromMediaId(
                                ACTION_SHUFFLE_PREFIX + listKey,
                                null
                            )
                        }
                    }
                )
            }
        }
    }

    private fun maybeRequestPostNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (
            prefs.getBoolean(KEY_NOTIF_PROMPTED, false) &&
            prefs.getInt(KEY_NOTIF_PROMPT_STATE, NOTIF_PROMPT_UNKNOWN) == NOTIF_PROMPT_UNKNOWN
        ) {
            // Migrate legacy "already prompted" flag to the new explicit prompt state.
            prefs.edit()
                .putInt(KEY_NOTIF_PROMPT_STATE, NOTIF_PROMPT_REQUESTED)
                .commit()
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (granted || enabled) {
            prefs.edit()
                .putBoolean(KEY_NOTIF_PROMPTED, true)
                .putInt(KEY_NOTIF_PROMPT_STATE, NOTIF_PROMPT_GRANTED)
                .commit()
            return
        }
        val promptState = prefs.getInt(KEY_NOTIF_PROMPT_STATE, NOTIF_PROMPT_UNKNOWN)
        if (promptState != NOTIF_PROMPT_UNKNOWN) return
        prefs.edit()
            .putBoolean(KEY_NOTIF_PROMPTED, true)
            .putInt(KEY_NOTIF_PROMPT_STATE, NOTIF_PROMPT_REQUESTED)
            .commit()
        requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onDestroy() {
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser?.disconnect()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun pushPlaybackState() {
        val state = lastPlaybackState?.state ?: PlaybackStateCompat.STATE_NONE
        val mediaId = lastMetadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        val trackName = lastMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        val artistName = lastMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        val durationMs = lastMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
        val positionMs = (lastPlaybackState?.position ?: 0L).coerceAtLeast(0L)
        val updatedAtMs = (lastPlaybackState?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)
        val speed = lastPlaybackState?.playbackSpeed ?: if (state == PlaybackStateCompat.STATE_PLAYING) {
            1f
        } else {
            0f
        }
        viewModel.updatePlaybackState(
            state = state,
            mediaId = mediaId,
            trackName = trackName,
            artistName = artistName,
            positionMs = positionMs,
            positionUpdatedAtElapsedMs = updatedAtMs,
            playbackSpeed = speed,
            durationMs = durationMs
        )
    }

    private fun pushQueueState() {
        val controller = mediaController ?: return
        val queueTitle = controller.queueTitle?.toString()
        val queueItems = controller.queue?.map { item ->
            val description: MediaDescriptionCompat = item.description
            QueueEntry(
                queueId = item.queueId,
                mediaId = description.mediaId,
                title = description.title?.toString() ?: description.mediaId ?: "Unknown"
            )
        } ?: emptyList()
        val activeQueueId = lastPlaybackState?.activeQueueItemId ?: -1L
        viewModel.updateQueueState(queueTitle, queueItems, activeQueueId)
    }

    private fun restoreLastTreeUri() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val playlistUriString = prefs.getString(KEY_PLAYLIST_TREE_URI, null)
        if (playlistUriString != null) {
            val playlistUri = Uri.parse(playlistUriString)
            val hasPlaylistPermission = contentResolver.persistedUriPermissions.any {
                it.uri == playlistUri && it.isReadPermission
            }
            if (hasPlaylistPermission) {
                viewModel.setPlaylistTreeUri(playlistUri, showMessage = false)
            } else {
                prefs.edit().remove(KEY_PLAYLIST_TREE_URI).apply()
            }
        }
        val limit = prefs.getInt(KEY_SCAN_LIMIT, pendingScanLimit)
        if (prefs.getBoolean(KEY_SCAN_WHOLE_DRIVE, false)) {
            if (hasMediaReadPermission()) {
                viewModel.scanWholeDevice(limit)
            } else {
                pendingWholeDriveScanLimit = limit
                requestMediaReadPermission.launch(requiredMediaReadPermission())
            }
            return
        }
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val uri = Uri.parse(uriString)
        val deepScan = prefs.getBoolean(KEY_SCAN_DEEP, false)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPermission) {
            prefs.edit().remove(KEY_TREE_URI).apply()
            viewModel.setFolderMessage("Folder access expired. Please select a folder again.")
            return
        }
        viewModel.setTreeUri(uri)
        pendingScanLimit = limit
        pendingDeepScan = deepScan
        viewModel.onDirectorySelected(uri, limit, deepScan = deepScan)
    }

    private fun sendFilesToServiceIfNeeded(files: List<com.example.mymediaplayer.shared.MediaFileInfo>) {
        val controller = mediaController ?: return
        if (files.size > MAX_MEDIA_FILES_FOR_BUNDLE) {
            if (lastSentLargeLibraryCount == files.size) return
            controller.transportControls.sendCustomAction(ACTION_REFRESH_LIBRARY, null)
            lastSentLargeLibraryCount = files.size
            return
        }
        val uris = files.map { it.uriString }
        if (uris == lastSentUris) return

        val names = files.map { it.displayName }
        val sizes = files.map { it.sizeBytes }.toLongArray()
        val titles = files.map { it.title.orEmpty() }
        val artists = files.map { it.artist.orEmpty() }
        val albums = files.map { it.album.orEmpty() }
        val genres = files.map { it.genre.orEmpty() }
        val durations = files.map { it.durationMs ?: -1L }.toLongArray()
        val years = files.map { it.year ?: 0 }.toIntArray()
        val addedAt = files.map { it.addedAtMs ?: -1L }.toLongArray()

        val bundle = Bundle().apply {
            putStringArrayList(EXTRA_URIS, ArrayList(uris))
            putStringArrayList(EXTRA_NAMES, ArrayList(names))
            putLongArray(EXTRA_SIZES, sizes)
            putStringArrayList(EXTRA_TITLES, ArrayList(titles))
            putStringArrayList(EXTRA_ARTISTS, ArrayList(artists))
            putStringArrayList(EXTRA_ALBUMS, ArrayList(albums))
            putStringArrayList(EXTRA_GENRES, ArrayList(genres))
            putLongArray(EXTRA_DURATIONS, durations)
            putIntArray(EXTRA_YEARS, years)
            putLongArray(EXTRA_ADDED_AT, addedAt)
        }
        controller.transportControls.sendCustomAction(ACTION_SET_MEDIA_FILES, bundle)
        lastSentUris = uris
        lastSentLargeLibraryCount = null
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

    private fun playSearchResults(songs: List<com.example.mymediaplayer.shared.MediaFileInfo>, shuffle: Boolean) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return
        if (songs.size > MAX_MEDIA_FILES_FOR_BUNDLE) {
            val target = if (shuffle) songs.random() else songs.first()
            sendFilesToServiceIfNeeded(viewModel.uiState.value.scan.scannedFiles)
            controller.transportControls?.playFromMediaId(target.uriString, null)
            return
        }
        val uris = songs.map { it.uriString }
        val bundle = Bundle().apply {
            putStringArrayList(EXTRA_SEARCH_URIS, ArrayList(uris))
            putBoolean(EXTRA_SEARCH_SHUFFLE, shuffle)
        }
        controller.transportControls.sendCustomAction(ACTION_PLAY_SEARCH_LIST, bundle)
    }

    private fun playUiList(
        songs: List<com.example.mymediaplayer.shared.MediaFileInfo>,
        shuffle: Boolean,
        queueTitle: String
    ) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return
        sendFilesToServiceIfNeeded(viewModel.uiState.value.scan.scannedFiles)
        if (songs.size > MAX_MEDIA_FILES_FOR_BUNDLE) {
            val target = if (shuffle) songs.random() else songs.first()
            controller.transportControls.playFromMediaId(target.uriString, null)
            return
        }
        val uris = songs.map { it.uriString }
        val bundle = Bundle().apply {
            putStringArrayList(EXTRA_LIST_URIS, ArrayList(uris))
            putBoolean(EXTRA_LIST_SHUFFLE, shuffle)
            putString(EXTRA_LIST_TITLE, queueTitle)
        }
        controller.transportControls.sendCustomAction(ACTION_PLAY_UI_LIST, bundle)
    }

    private fun queueTitleForCurrentUiList(state: MainUiState): String {
        val lib = state.library
        return when (lib.selectedTab) {
            LibraryTab.Albums -> lib.selectedAlbum ?: "Albums"
            LibraryTab.Genres -> lib.selectedGenre ?: "Genres"
            LibraryTab.Artists -> lib.selectedArtist ?: "Artists"
            LibraryTab.Decades -> lib.selectedDecade ?: "Decades"
            else -> "All Songs"
        }
    }

    private fun sendTrackVoiceIntroSettingToService() {
        val controller = mediaController ?: return
        val bundle = Bundle().apply {
            putBoolean(EXTRA_TRACK_VOICE_INTRO_ENABLED, trackVoiceIntroEnabled)
        }
        controller.transportControls.sendCustomAction(ACTION_SET_TRACK_VOICE_INTRO, bundle)
    }

    private fun sendTrackVoiceOutroSettingToService() {
        val controller = mediaController ?: return
        val bundle = Bundle().apply {
            putBoolean(EXTRA_TRACK_VOICE_OUTRO_ENABLED, trackVoiceOutroEnabled)
        }
        controller.transportControls.sendCustomAction(ACTION_SET_TRACK_VOICE_OUTRO, bundle)
    }

    private fun sendDebugCloudSettingToService(enabled: Boolean) {
        val controller = mediaController ?: return
        val bundle = Bundle().apply {
            putBoolean("debug_cloud_enabled", enabled)
        }
        controller.transportControls.sendCustomAction(ACTION_SET_DEBUG_CLOUD, bundle)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != ACTION_MEDIA_PLAY_FROM_SEARCH) return
        pendingVoiceSearchQuery = intent.getStringExtra(SearchManager.QUERY) ?: ""
        pendingVoiceSearchExtras = intent.extras?.let { Bundle(it) }
        dispatchPendingVoiceSearchIfNeeded()
    }

    private fun dispatchPendingVoiceSearchIfNeeded() {
        val controller = mediaController ?: return
        val query = pendingVoiceSearchQuery ?: return
        val extras = pendingVoiceSearchExtras ?: Bundle()
        controller.transportControls.playFromSearch(query, extras)
        pendingVoiceSearchQuery = null
        pendingVoiceSearchExtras = null
    }

    private fun toggleBluetoothAutoPlay() {
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothConnectPermission()
            return
        }
        bluetoothAutoPlayEnabled = !bluetoothAutoPlayEnabled
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BT_AUTOPLAY_ENABLED, bluetoothAutoPlayEnabled)
            .apply()
        refreshBluetoothState()
        Toast.makeText(
            this,
            if (bluetoothAutoPlayEnabled) "Bluetooth auto-play enabled" else "Bluetooth auto-play disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleTrackVoiceIntro() {
        trackVoiceIntroEnabled = !trackVoiceIntroEnabled
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TRACK_VOICE_INTRO_ENABLED, trackVoiceIntroEnabled)
            .apply()
        sendTrackVoiceIntroSettingToService()
        Toast.makeText(
            this,
            if (trackVoiceIntroEnabled) "Track voice intro enabled" else "Track voice intro disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleTrackVoiceOutro() {
        trackVoiceOutroEnabled = !trackVoiceOutroEnabled
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TRACK_VOICE_OUTRO_ENABLED, trackVoiceOutroEnabled)
            .apply()
        sendTrackVoiceOutroSettingToService()
        Toast.makeText(
            this,
            if (trackVoiceOutroEnabled) "Track voice outro enabled" else "Track voice outro disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun addCurrentBluetoothDeviceToAllowlist() {
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothConnectPermission()
            return
        }
        val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val connected = mutableListOf<BluetoothDevice>()
        connected += manager?.getConnectedDevices(BluetoothProfile.A2DP).orEmpty()
        connected += manager?.getConnectedDevices(BluetoothProfile.HEADSET).orEmpty()
        val additions = connected.mapNotNull { device ->
            val address = runCatching { device.address }.getOrNull() ?: return@mapNotNull null
            val name = runCatching { device.name }.getOrNull()
            address to name
        }.toMap()
        if (additions.isEmpty()) {
            Toast.makeText(this, "No connected Bluetooth audio device", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val existing = readTrustedBluetoothDevices(prefs).toMutableMap()
        existing.putAll(additions)
        persistTrustedBluetoothDevices(prefs, existing)
        refreshBluetoothState()
        Toast.makeText(this, "Trusted ${additions.size} Bluetooth device(s)", Toast.LENGTH_SHORT).show()
    }

    private fun removeTrustedBluetoothDevice(address: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val existing = readTrustedBluetoothDevices(prefs).toMutableMap()
        if (existing.remove(address) == null) return
        persistTrustedBluetoothDevices(prefs, existing)
        refreshBluetoothState()
        Toast.makeText(this, "Removed trusted device", Toast.LENGTH_SHORT).show()
    }

    private fun clearTrustedBluetoothDevices() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        persistTrustedBluetoothDevices(prefs, emptyMap())
        refreshBluetoothState()
        Toast.makeText(this, "Cleared trusted devices", Toast.LENGTH_SHORT).show()
    }

    private fun requestBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requiredMediaReadPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasMediaReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            requiredMediaReadPermission()
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasValidPlaylistSaveFolder(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val uriString = prefs.getString(KEY_PLAYLIST_TREE_URI, null) ?: return false
        val uri = Uri.parse(uriString)
        return contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == uri && perm.isReadPermission
        }
    }

    private fun refreshBluetoothState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        trustedBluetoothDevices = readTrustedBluetoothDevices(prefs)
            .map { (address, name) -> TrustedBluetoothDevice(address = address, name = name) }
            .sortedWith(
                compareBy<TrustedBluetoothDevice> { it.name?.lowercase(Locale.US) ?: "\uFFFF" }
                    .thenBy { it.address.lowercase(Locale.US) }
            )
        val lastEvent = prefs.getLong(KEY_BT_LAST_EVENT_MS, 0L)
        val lastTrigger = prefs.getLong(KEY_BT_LAST_AUTOPLAY_MS, 0L)
        val lastReason = prefs.getString(KEY_BT_LAST_REASON, "none") ?: "none"
        val lastDevice = prefs.getString(KEY_BT_LAST_DEVICE, null)
        val lastDeviceName = prefs.getString(KEY_BT_LAST_DEVICE_NAME, null)
        bluetoothDiagnostics = buildString {
            appendLine("Enabled: ${if (bluetoothAutoPlayEnabled) "Yes" else "No"}")
            appendLine("Trusted devices: ${trustedBluetoothDevices.size}")
            appendLine("Last reason: $lastReason")
            val displayDevice = when {
                !lastDeviceName.isNullOrBlank() && !lastDevice.isNullOrBlank() ->
                    "$lastDeviceName ($lastDevice)"
                !lastDeviceName.isNullOrBlank() -> lastDeviceName
                !lastDevice.isNullOrBlank() -> lastDevice
                else -> "n/a"
            }
            appendLine("Last device: $displayDevice")
            appendLine(
                "Last event (elapsed): " +
                    if (lastEvent > 0L) formatElapsed(lastEvent) else "n/a"
            )
            append(
                "Last trigger (elapsed): " +
                    if (lastTrigger > 0L) formatElapsed(lastTrigger) else "n/a"
            )
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun readTrustedBluetoothDevices(
        prefs: android.content.SharedPreferences
    ): Map<String, String?> {
        val raw = prefs.getString(KEY_BT_AUTOPLAY_DEVICES, null).orEmpty()
        val decoded = mutableMapOf<String, String?>()
        if (raw.isNotBlank()) {
            raw.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('\t', limit = 2)
                val address = parts[0].trim()
                if (address.isBlank()) return@forEach
                val name = parts.getOrNull(1)?.trim()?.ifBlank { null }
                decoded[address] = name
            }
        }
        val legacy = prefs.getStringSet(KEY_BT_AUTOPLAY_ADDRESSES, emptySet()) ?: emptySet()
        legacy.forEach { address ->
            if (address.isNotBlank() && address !in decoded) {
                decoded[address] = null
            }
        }
        return decoded
    }

    private fun persistTrustedBluetoothDevices(
        prefs: android.content.SharedPreferences,
        devices: Map<String, String?>
    ) {
        val clean = devices
            .filterKeys { it.isNotBlank() }
            .toSortedMap()
        val encoded = clean.entries.joinToString("\n") { entry ->
            val safeName = entry.value?.replace('\n', ' ')?.replace('\t', ' ') ?: ""
            "${entry.key}\t$safeName"
        }
        prefs.edit()
            .putString(KEY_BT_AUTOPLAY_DEVICES, encoded)
            .putStringSet(KEY_BT_AUTOPLAY_ADDRESSES, clean.keys)
            .apply()
    }

}
