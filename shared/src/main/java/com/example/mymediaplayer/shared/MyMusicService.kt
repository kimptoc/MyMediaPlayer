package com.example.mymediaplayer.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.app.SearchManager
import androidx.annotation.VisibleForTesting
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media.MediaBrowserServiceCompat
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import androidx.media.utils.MediaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

class MyMusicService : MediaBrowserServiceCompat() {

    companion object {
        private const val ROOT_ID = "root"
        private const val HOME_ID = "home"
        private const val SONGS_ID = "songs"
        private const val SONGS_ALL_ID = "songs_all"
        private const val PLAYLISTS_ID = "playlists"
        private const val ALBUMS_ID = "albums"
        private const val GENRES_ID = "genres"
        private const val ARTISTS_ID = "artists"
        private const val DECADES_ID = "decades"
        private const val SEARCH_ID = "search"
        private const val PLAYLIST_PREFIX = "playlist:"
        private const val SMART_PLAYLIST_PREFIX = "smart_playlist:"
        private const val PLAYLIST_URI_PREFIX = "playlist_uri:"
        private const val ALBUM_PREFIX = "album:"
        private const val GENRE_PREFIX = "genre:"
        private const val GENRE_SONG_LETTER_PREFIX = "genre_song_letter:"
        private const val ARTIST_PREFIX = "artist:"
        private const val DECADE_PREFIX = "decade:"
        private const val DECADE_SONG_LETTER_PREFIX = "decade_song_letter:"
        private const val ARTIST_LETTER_PREFIX = "artist_letter:"
        private const val GENRE_LETTER_PREFIX = "genre_letter:"
        private const val SONG_LETTER_PREFIX = "song_letter:"
        private const val SONG_BUCKET_THRESHOLD = 500
        private const val ACTION_PLAY_ALL_PREFIX = "action:play_all:"
        private const val ACTION_SHUFFLE_PREFIX = "action:shuffle:"
        private const val PREFS_NAME = "mymediaplayer_prefs"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_SCAN_LIMIT = "scan_limit"
        private const val KEY_RESUME_MEDIA_URI = "resume_media_uri"
        private const val KEY_RESUME_QUEUE_URIS = "resume_queue_uris"
        private const val KEY_RESUME_QUEUE_INDEX = "resume_queue_index"
        private const val KEY_RESUME_QUEUE_TITLE = "resume_queue_title"
        private const val KEY_RESUME_POSITION_MS = "resume_position_ms"
        private const val KEY_RESUME_REPEAT_MODE = "resume_repeat_mode"
        private const val KEY_FAVORITE_URIS = "favorite_uris"
        private const val KEY_PLAY_COUNTS = "play_counts"
        private const val KEY_LAST_PLAYED_AT = "last_played_at"
        private const val KEY_TRACK_VOICE_INTRO_ENABLED = "track_voice_intro_enabled"
        private const val KEY_TRACK_VOICE_OUTRO_ENABLED = "track_voice_outro_enabled"
        private const val SMART_PLAYLIST_FAVORITES = "favorites"
        private const val SMART_PLAYLIST_RECENTLY_ADDED = "recently_added"
        private const val SMART_PLAYLIST_MOST_PLAYED = "most_played"
        private const val SMART_PLAYLIST_NOT_HEARD_RECENTLY = "not_heard_recently"

        private const val ACTION_SET_MEDIA_FILES = "SET_MEDIA_FILES"
        private const val ACTION_REFRESH_LIBRARY = "REFRESH_LIBRARY"
        private const val ACTION_SET_PLAYLISTS = "SET_PLAYLISTS"
        private const val ACTION_PLAY_SEARCH_LIST = "PLAY_SEARCH_LIST"
        private const val ACTION_PLAY_UI_LIST = "PLAY_UI_LIST"
        private const val ACTION_SET_TRACK_VOICE_INTRO = "SET_TRACK_VOICE_INTRO"
        private const val ACTION_SET_TRACK_VOICE_OUTRO = "SET_TRACK_VOICE_OUTRO"
        const val ACTION_BT_AUTOPLAY = "BT_AUTOPLAY"
        private const val ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"

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

        private const val MAX_CONSECUTIVE_PLAYBACK_ERRORS = 3
        private const val TTS_SPEECH_RATE = 0.93f
        private const val TTS_PITCH = 1.04f

        private const val NOW_PLAYING_CHANNEL_ID = "now_playing"
        private const val NOW_PLAYING_NOTIFICATION_ID = 1001

        private const val EXTRA_MEDIA_FOCUS_KEY = "android.intent.extra.focus"
        private const val EXTRA_MEDIA_ARTIST_KEY = "android.intent.extra.artist"
        private const val EXTRA_MEDIA_ALBUM_KEY = "android.intent.extra.album"
        private const val EXTRA_MEDIA_GENRE_KEY = "android.intent.extra.genre"
        private const val EXTRA_MEDIA_TITLE_KEY = "android.intent.extra.title"
        private const val EXTRA_MEDIA_PLAYLIST_KEY = "android.intent.extra.playlist"
        private const val FOCUS_PLAYLIST = "vnd.android.cursor.dir/playlist"
        private const val FOCUS_ARTIST = "vnd.android.cursor.dir/artist"
        private const val FOCUS_ALBUM = "vnd.android.cursor.dir/album"
        private const val FOCUS_GENRE = "vnd.android.cursor.dir/genre"
        private const val FOCUS_TITLE = "vnd.android.cursor.item/audio"
    }

    private data class PendingSpeechAction(
        val utteranceId: String,
        val onComplete: () -> Unit
    )

    private lateinit var session: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    private val mediaCacheService = MediaCacheService()
    private val playlistService = PlaylistService()
    private var currentFileInfo: MediaFileInfo? = null
    private var currentMediaId: String? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playMutex = Mutex()
    private var playJob: Job? = null
    private lateinit var notificationManager: NotificationManagerCompat
    private var lastMetadata: MediaMetadataCompat? = null

    private var playlistQueue: List<MediaFileInfo> = emptyList()
    private var currentQueueIndex: Int = -1
    private var currentPlaylistName: String? = null
    private var repeatMode: Int = PlaybackStateCompat.REPEAT_MODE_NONE
    private var pendingResumePositionMs: Long? = null
    private var resumeOnAudioFocusGain: Boolean = false
    private var isDuckedForFocusLoss: Boolean = false
    private var lastBrowseParentId: String? = null
    private var lastSearchQuery: String? = null
    private var lastSearchResults: List<MediaFileInfo> = emptyList()
    private var consecutivePlaybackErrors: Int = 0
    @Volatile
    private var isScanning: Boolean = false
    private val pendingResults =
        mutableMapOf<String, MutableList<MediaBrowserServiceCompat.Result<MutableList<MediaItem>>>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var ttsInitializing: Boolean = false
    private val pendingSpeechAction = AtomicReference<PendingSpeechAction?>(null)
    private var trackVoiceIntroEnabled: Boolean = false
    private var trackVoiceOutroEnabled: Boolean = false
    private var lastIntroTemplateIndex: Int = -1
    private var lastOutroTemplateIndex: Int = -1

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnAudioFocusGain = false
                unduckIfNeeded()
                handlePause()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnAudioFocusGain = mediaPlayer?.isPlaying == true
                unduckIfNeeded()
                handlePause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val player = mediaPlayer ?: return@OnAudioFocusChangeListener
                if (player.isPlaying) {
                    player.setVolume(0.25f, 0.25f)
                    isDuckedForFocusLoss = true
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                unduckIfNeeded()
                if (resumeOnAudioFocusGain && mediaPlayer != null && mediaPlayer?.isPlaying == false) {
                    mediaPlayer?.start()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    savePlaybackSnapshot()
                }
                resumeOnAudioFocusGain = false
            }
        }
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            resumeOnAudioFocusGain = false
            if (mediaPlayer == null) {
                val resumeFromQueue =
                    if (playlistQueue.isNotEmpty() && currentQueueIndex in playlistQueue.indices) {
                        playlistQueue[currentQueueIndex]
                    } else {
                        null
                    }
                val resumeTrack = resumeFromQueue ?: currentFileInfo
                if (resumeTrack != null) {
                    updateSessionQueue()
                    playTrack(resumeTrack)
                }
                return
            }
            if (!requestAudioFocus()) return
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val resolvedMediaId = mediaId ?: return
            playJob?.cancel()
            playJob = serviceScope.launch {
                playMutex.withLock {
                    handlePlayFromMediaId(resolvedMediaId)
                }
            }
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            playJob?.cancel()
            playJob = serviceScope.launch {
                playMutex.withLock {
                    handlePlayFromSearch(query, extras)
                }
            }
        }

        override fun onPause() {
            resumeOnAudioFocusGain = false
            unduckIfNeeded()
            handlePause()
        }

        override fun onStop() {
            resumeOnAudioFocusGain = false
            unduckIfNeeded()
            handleStop()
        }

        override fun onSkipToNext() {
            if (playlistQueue.isEmpty()) return
            val nextIndex = currentQueueIndex + 1
            if (nextIndex >= playlistQueue.size) {
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL && playlistQueue.isNotEmpty()) {
                    currentQueueIndex = 0
                    updateSessionQueue()
                    playTrack(playlistQueue[currentQueueIndex])
                } else {
                    handleStop()
                }
                return
            }
            currentQueueIndex = nextIndex
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
        }

        override fun onSkipToPrevious() {
            if (playlistQueue.isEmpty()) return
            val previousIndex = currentQueueIndex - 1
            if (previousIndex < 0) {
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL && playlistQueue.isNotEmpty()) {
                    currentQueueIndex = playlistQueue.size - 1
                    updateSessionQueue()
                    playTrack(playlistQueue[currentQueueIndex])
                }
                return
            }
            currentQueueIndex = previousIndex
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
        }

        override fun onSkipToQueueItem(id: Long) {
            if (playlistQueue.isEmpty()) return
            val targetIndex = id.toInt()
            if (targetIndex !in playlistQueue.indices) return
            currentQueueIndex = targetIndex
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
        }

        override fun onSetRepeatMode(repeatMode: Int) {
            this@MyMusicService.repeatMode = repeatMode
            session.setRepeatMode(repeatMode)
            savePlaybackSnapshot()
        }

        override fun onSeekTo(pos: Long) {
            val player = mediaPlayer ?: return
            val duration = runCatching { player.duration.toLong() }.getOrDefault(0L)
            val clamped = if (duration > 0L) pos.coerceIn(0L, duration) else pos.coerceAtLeast(0L)
            runCatching { player.seekTo(clamped.toInt()) }
            val state = lastPlaybackState()?.state ?: if (player.isPlaying) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }
            updatePlaybackState(state)
            savePlaybackSnapshot(positionMsOverride = clamped)
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                ACTION_SET_MEDIA_FILES -> {
                    if (extras == null) return
                    val uris = extras.getStringArrayList(EXTRA_URIS) ?: return
                    val names = extras.getStringArrayList(EXTRA_NAMES) ?: return
                    val sizes = extras.getLongArray(EXTRA_SIZES) ?: return
                    val titles = extras.getStringArrayList(EXTRA_TITLES)
                    val artists = extras.getStringArrayList(EXTRA_ARTISTS)
                    val albums = extras.getStringArrayList(EXTRA_ALBUMS)
                    val genres = extras.getStringArrayList(EXTRA_GENRES)
                    val durations = extras.getLongArray(EXTRA_DURATIONS)
                    val years = extras.getIntArray(EXTRA_YEARS)
                    val addedAt = extras.getLongArray(EXTRA_ADDED_AT)

                    mediaCacheService.clearFiles()
                    val count = minOf(uris.size, names.size, sizes.size)
                    for (i in 0 until count) {
                        val title = titles?.getOrNull(i).orEmpty().ifBlank { names[i] }
                        val artist = artists?.getOrNull(i).orEmpty().ifBlank { null }
                        val album = albums?.getOrNull(i).orEmpty().ifBlank { null }
                        val genre = genres?.getOrNull(i).orEmpty().ifBlank { null }
                        val durationMs = durations?.getOrNull(i)?.takeIf { it >= 0L }
                        val year = years?.getOrNull(i)?.takeIf { it > 0 }
                        val addedAtMs = addedAt?.getOrNull(i)?.takeIf { it >= 0L }
                        mediaCacheService.addFile(
                            MediaFileInfo(
                                uriString = uris[i],
                                displayName = names[i],
                                sizeBytes = sizes[i],
                                title = title,
                                artist = artist,
                                album = album,
                                genre = genre,
                                durationMs = durationMs,
                                year = year,
                                addedAtMs = addedAtMs
                            )
                        )
                    }
                    serviceScope.launch {
                        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        val treeUriStr = prefs.getString(KEY_TREE_URI, null)
                        if (treeUriStr != null) {
                            val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
                            mediaCacheService.persistCache(this@MyMusicService, Uri.parse(treeUriStr), limit)
                        }
                    }
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(HOME_ID)
                    notifyChildrenChanged(SONGS_ID)
                    notifyChildrenChanged(ALBUMS_ID)
                    notifyChildrenChanged(GENRES_ID)
                    notifyChildrenChanged(ARTISTS_ID)
                    notifyChildrenChanged(DECADES_ID)
                }
                ACTION_REFRESH_LIBRARY -> {
                    loadCachedTreeIfAvailable()
                }
                ACTION_PLAY_SEARCH_LIST -> {
                    if (extras == null) return
                    val uris = extras.getStringArrayList(EXTRA_SEARCH_URIS) ?: return
                    val shuffle = extras.getBoolean(EXTRA_SEARCH_SHUFFLE, false)
                    playProvidedUriList(
                        uris = uris,
                        shuffle = shuffle,
                        queueTitle = "Search Results",
                        setSearchResults = true
                    )
                }
                ACTION_PLAY_UI_LIST -> {
                    if (extras == null) return
                    val uris = extras.getStringArrayList(EXTRA_LIST_URIS) ?: return
                    val shuffle = extras.getBoolean(EXTRA_LIST_SHUFFLE, false)
                    val queueTitle = extras.getString(EXTRA_LIST_TITLE).orEmpty()
                    playProvidedUriList(
                        uris = uris,
                        shuffle = shuffle,
                        queueTitle = if (queueTitle.isNotBlank()) queueTitle else "All Songs",
                        setSearchResults = false
                    )
                }
                ACTION_SET_PLAYLISTS -> {
                    if (extras == null) return
                    val playlistUris = extras.getStringArrayList(EXTRA_PLAYLIST_URIS) ?: return
                    val playlistNames = extras.getStringArrayList(EXTRA_PLAYLIST_NAMES) ?: return

                    mediaCacheService.clearPlaylists()
                    val count = minOf(playlistUris.size, playlistNames.size)
                    for (i in 0 until count) {
                        mediaCacheService.addPlaylist(
                            PlaylistInfo(
                                uriString = playlistUris[i],
                                displayName = playlistNames[i]
                            )
                        )
                    }
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(PLAYLISTS_ID)
                }
                ACTION_SET_TRACK_VOICE_INTRO -> {
                    val enabled = extras?.getBoolean(EXTRA_TRACK_VOICE_INTRO_ENABLED) ?: false
                    trackVoiceIntroEnabled = enabled
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_TRACK_VOICE_INTRO_ENABLED, enabled)
                        .apply()
                    if (enabled) {
                        ensureTextToSpeechInitialized()
                    } else if (!trackVoiceOutroEnabled) {
                        clearPendingIntro()
                    }
                }
                ACTION_SET_TRACK_VOICE_OUTRO -> {
                    val enabled = extras?.getBoolean(EXTRA_TRACK_VOICE_OUTRO_ENABLED) ?: false
                    trackVoiceOutroEnabled = enabled
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_TRACK_VOICE_OUTRO_ENABLED, enabled)
                        .apply()
                    if (enabled) {
                        ensureTextToSpeechInitialized()
                    } else if (!trackVoiceIntroEnabled) {
                        clearPendingIntro()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = NotificationManagerCompat.from(this)
        ensureNotificationChannel()

        session = MediaSessionCompat(this, "MyMusicService")
        session.setSessionActivity(buildLaunchIntent())
        sessionToken = session.sessionToken
        session.setCallback(callback)
        @Suppress("DEPRECATION")
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        playbackStateBuilder
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
            )
            .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f, SystemClock.elapsedRealtime())
        session.setPlaybackState(playbackStateBuilder.build())
        session.setRepeatMode(repeatMode)
        session.isActive = true

        trackVoiceIntroEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_TRACK_VOICE_INTRO_ENABLED, false)
        trackVoiceOutroEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_TRACK_VOICE_OUTRO_ENABLED, false)
        if (trackVoiceIntroEnabled || trackVoiceOutroEnabled) {
            ensureTextToSpeechInitialized()
        }

        restorePlaybackSnapshot()
        loadCachedTreeIfAvailable()
    }

    private fun playProvidedUriList(
        uris: List<String>,
        shuffle: Boolean,
        queueTitle: String,
        setSearchResults: Boolean
    ) {
        if (uris.isEmpty()) return
        val snapshot = mediaCacheService.cachedFiles
        val lookup = snapshot.associateBy { it.uriString }
        val list = uris.map { uri ->
            lookup[uri] ?: MediaFileInfo(
                uriString = uri,
                displayName = uri,
                sizeBytes = 0L,
                title = uri
            )
        }
        lastSearchQuery = null
        lastSearchResults = if (setSearchResults) list else emptyList()
        playlistQueue = if (shuffle) list.shuffled() else list
        currentQueueIndex = 0
        currentPlaylistName = queueTitle
        updateSessionQueue()
        playTrack(playlistQueue[currentQueueIndex])
    }

    override fun onDestroy() {
        val lastPosition = currentPositionSafeMs()
        savePlaybackSnapshot(positionMsOverride = lastPosition)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOW_PLAYING_NOTIFICATION_ID)
        serviceScope.cancel()
        releaseMediaPlayer()
        abandonAudioFocus()
        synchronized(pendingResults) {
            pendingResults.clear()
        }
        playlistQueue = emptyList()
        lastSearchResults = emptyList()
        session.setCallback(null)
        session.isActive = false
        session.release()
        clearPendingIntro()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
        ttsInitializing = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BT_AUTOPLAY) {
            session.controller.transportControls.play()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            val extras = intent.extras?.let { Bundle(it) }
            playJob?.cancel()
            playJob = serviceScope.launch {
                playMutex.withLock {
                    handlePlayFromSearch(query, extras)
                }
            }
            return START_NOT_STICKY
        }
        MediaButtonReceiver.handleIntent(session, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot {
        val rootExtras = Bundle().apply {
            putInt(
                MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
                MediaItem.FLAG_BROWSABLE
            )
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
            )
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        return MediaBrowserServiceCompat.BrowserRoot(ROOT_ID, rootExtras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        if (isScanning) {
            synchronized(pendingResults) {
                val list = pendingResults.getOrPut(parentId) { mutableListOf() }
                list.add(result)
            }
            result.detach()
            return
        }

        lastBrowseParentId = parentId

        if (shouldLoadChildrenAsync(parentId, mediaCacheService.hasAlbumArtistIndexes())) {
            result.detach()
            serviceScope.launch {
                try {
                    ensureMetadataIndexes()
                    result.sendResult(buildMediaItems(parentId))
                } catch (e: Exception) {
                    Log.e("MyMusicService", "Failed to build media items for $parentId", e)
                    result.sendResult(mutableListOf())
                }
            }
            return
        }

        result.sendResult(buildMediaItems(parentId))
    }

    override fun onSearch(query: String, extras: Bundle?, result: Result<MutableList<MediaItem>>) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            lastSearchQuery = null
            lastSearchResults = emptyList()
            result.sendResult(mutableListOf())
            return
        }
        val start = SystemClock.elapsedRealtime()
        val matches = mediaCacheService.searchFiles(trimmed)
        lastSearchQuery = trimmed
        lastSearchResults = matches
        val songIconUri = resourceIconUri(R.drawable.ic_album_placeholder)
        val items = matches.map { fileInfo ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(fileInfo.uriString)
                .setTitle(fileInfo.cleanTitle)
                .setSubtitle(fileInfo.artist)
                .setIconUri(songIconUri)
                .build()
            MediaItem(description, MediaItem.FLAG_PLAYABLE)
        }.toMutableList()
        val elapsed = SystemClock.elapsedRealtime() - start
        Log.d("MyMusicService", "onSearch($trimmed) -> ${items.size} in ${elapsed}ms")
        result.sendResult(items)
    }

    internal fun buildMediaItems(parentId: String): MutableList<MediaItem> {
        val start = SystemClock.elapsedRealtime()
        if (parentId.startsWith(SMART_PLAYLIST_PREFIX)) {
            val smartId = Uri.decode(parentId.removePrefix(SMART_PLAYLIST_PREFIX))
            val tracks = resolveSmartPlaylistTracksById(smartId) ?: emptyList()
            return buildSongListItems(
                tracks,
                SMART_PLAYLIST_PREFIX + Uri.encode(smartId),
                resourceIconUri(R.drawable.ic_album_placeholder)
            )
        }
        if (parentId.startsWith(PLAYLIST_PREFIX)) {
            val playlistUri = Uri.decode(parentId.removePrefix(PLAYLIST_PREFIX))
            val songs = enrichFromCache(
                playlistService.readPlaylist(this, Uri.parse(playlistUri))
            )
            val songIconUri = resourceIconUri(R.drawable.ic_album_placeholder)
            return buildSongListItems(songs, PLAYLIST_URI_PREFIX + Uri.encode(playlistUri), songIconUri)
        }
        if (parentId.startsWith(ALBUM_PREFIX)) {
            ensureMetadataIndexes()
            val album = Uri.decode(parentId.removePrefix(ALBUM_PREFIX))
            return buildSongListItems(mediaCacheService.songsForAlbum(album), ALBUM_PREFIX + Uri.encode(album), resourceIconUri(R.drawable.ic_album_placeholder))
        }
        if (parentId.startsWith(GENRE_PREFIX)) {
            ensureMetadataIndexes()
            val genre = Uri.decode(parentId.removePrefix(GENRE_PREFIX))
            val songs = mediaCacheService.songsForGenre(genre)
            if (shouldBucketSongs(songs.size)) {
                return buildSongBucketItems(
                    songs,
                    GENRE_PREFIX + Uri.encode(genre),
                    GENRE_SONG_LETTER_PREFIX + Uri.encode(genre) + ":"
                )
            }
            return buildSongListItems(songs, GENRE_PREFIX + Uri.encode(genre), resourceIconUri(R.drawable.ic_album_placeholder))
        }
        if (parentId.startsWith(GENRE_SONG_LETTER_PREFIX)) {
            ensureMetadataIndexes()
            val parts = parseBucketParts(parentId, GENRE_SONG_LETTER_PREFIX) ?: return mutableListOf()
            val genre = parts.first
            val letter = parts.second
            val songs = mediaCacheService.songsForGenre(genre)
            val filtered = filterSongsByLetter(songs, letter)
            return buildSongLetterItems(
                GENRE_SONG_LETTER_PREFIX + Uri.encode(genre) + ":" + Uri.encode(letter),
                filtered
            )
        }
        if (parentId.startsWith(ARTIST_PREFIX)) {
            ensureMetadataIndexes()
            val artist = Uri.decode(parentId.removePrefix(ARTIST_PREFIX))
            return buildSongListItems(mediaCacheService.songsForArtist(artist), ARTIST_PREFIX + Uri.encode(artist), resourceIconUri(R.drawable.ic_album_placeholder))
        }
        if (parentId.startsWith(ARTIST_LETTER_PREFIX)) {
            ensureMetadataIndexes()
            val letter = Uri.decode(parentId.removePrefix(ARTIST_LETTER_PREFIX))
            return buildCategoryListItems(
                filterByLetter(mediaCacheService.artists(), letter),
                ARTIST_PREFIX,
                buildArtistCounts(mediaCacheService.cachedFiles),
                resourceIconUri(R.drawable.ic_auto_artists)
            )
        }
        if (parentId.startsWith(DECADE_PREFIX)) {
            ensureMetadataIndexes()
            val decade = Uri.decode(parentId.removePrefix(DECADE_PREFIX))
            val songs = mediaCacheService.songsForDecade(decade)
            if (shouldBucketSongs(songs.size)) {
                return buildSongBucketItems(
                    songs,
                    DECADE_PREFIX + Uri.encode(decade),
                    DECADE_SONG_LETTER_PREFIX + Uri.encode(decade) + ":"
                )
            }
            return buildSongListItems(songs, DECADE_PREFIX + Uri.encode(decade), resourceIconUri(R.drawable.ic_album_placeholder))
        }
        if (parentId.startsWith(DECADE_SONG_LETTER_PREFIX)) {
            ensureMetadataIndexes()
            val parts = parseBucketParts(parentId, DECADE_SONG_LETTER_PREFIX) ?: return mutableListOf()
            val decade = parts.first
            val letter = parts.second
            val songs = mediaCacheService.songsForDecade(decade)
            val filtered = filterSongsByLetter(songs, letter)
            return buildSongLetterItems(
                DECADE_SONG_LETTER_PREFIX + Uri.encode(decade) + ":" + Uri.encode(letter),
                filtered
            )
        }
        if (parentId.startsWith(GENRE_LETTER_PREFIX)) {
            ensureMetadataIndexes()
            val letter = Uri.decode(parentId.removePrefix(GENRE_LETTER_PREFIX))
            return buildCategoryListItems(
                filterByLetter(mediaCacheService.genres(), letter),
                GENRE_PREFIX,
                iconUri = resourceIconUri(R.drawable.ic_auto_genres)
            )
        }
        if (parentId.startsWith(SONG_LETTER_PREFIX)) {
            val letter = Uri.decode(parentId.removePrefix(SONG_LETTER_PREFIX))
            val filtered = filterSongsByLetter(mediaCacheService.cachedFiles, letter)
            return buildSongLetterItems(
                SONG_LETTER_PREFIX + Uri.encode(letter),
                filtered
            )
        }

        val items = when (parentId) {
            ROOT_ID -> {
                val items = mutableListOf<MediaItem>()
                items.add(
                    MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId(HOME_ID)
                            .setTitle("Home")
                            .setExtras(
                                bundleOfContentStyle(
                                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM
                                )
                            )
                            .build(),
                        MediaItem.FLAG_BROWSABLE
                    )
                )
                items.add(
                    MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId(SEARCH_ID)
                            .setTitle("Search")
                            .build(),
                        MediaItem.FLAG_BROWSABLE
                    )
                )
                items
            }
            HOME_ID -> buildHomeItems()
            SONGS_ID -> {
                val items = mutableListOf<MediaItem>()
                if (mediaCacheService.cachedFiles.isNotEmpty()) {
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(ACTION_PLAY_ALL_PREFIX + SONGS_ID)
                                .setTitle("[Play All]")
                                .build(),
                            MediaItem.FLAG_PLAYABLE
                        )
                    )
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(ACTION_SHUFFLE_PREFIX + SONGS_ID)
                                .setTitle("[Shuffle]")
                                .build(),
                            MediaItem.FLAG_PLAYABLE
                        )
                    )
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(SONGS_ALL_ID)
                                .setTitle("Browse All Songs")
                                .setIconUri(resourceIconUri(R.drawable.ic_auto_song))
                                .build(),
                            MediaItem.FLAG_BROWSABLE
                        )
                    )
                }
                items
        }
            SONGS_ALL_ID -> {
                val titles = mediaCacheService.cachedFiles.map { it.cleanTitle }
                buildCategoryListItems(
                    buildLetterBuckets(titles),
                    SONG_LETTER_PREFIX,
                    iconUri = resourceIconUri(R.drawable.ic_auto_song)
                )
            }
            PLAYLISTS_ID -> {
                val items = mutableListOf<MediaItem>()
                if (mediaCacheService.discoveredPlaylists.isNotEmpty() ||
                    mediaCacheService.cachedFiles.isNotEmpty()
                ) {
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(ACTION_PLAY_ALL_PREFIX + PLAYLISTS_ID)
                                .setTitle("[Play All]")
                                .build(),
                            MediaItem.FLAG_PLAYABLE
                        )
                    )
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(ACTION_SHUFFLE_PREFIX + PLAYLISTS_ID)
                                .setTitle("[Shuffle]")
                                .build(),
                            MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
                items += mediaCacheService.discoveredPlaylists.map { playlist ->
                    val description = MediaDescriptionCompat.Builder()
                        .setMediaId(PLAYLIST_PREFIX + Uri.encode(playlist.uriString))
                        .setTitle(playlist.displayName.removeSuffix(".m3u"))
                        .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                        .build()
                    MediaItem(description, MediaItem.FLAG_BROWSABLE)
                }
                val smartPlaylists = listOf(
                    SMART_PLAYLIST_FAVORITES to "Favorites",
                    SMART_PLAYLIST_RECENTLY_ADDED to "Recently Added",
                    SMART_PLAYLIST_MOST_PLAYED to "Most Played",
                    SMART_PLAYLIST_NOT_HEARD_RECENTLY to "Haven't Heard In A While"
                )
                items += smartPlaylists.map { smart ->
                    val description = MediaDescriptionCompat.Builder()
                        .setMediaId(SMART_PLAYLIST_PREFIX + Uri.encode(smart.first))
                        .setTitle(smart.second)
                        .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                        .build()
                    MediaItem(description, MediaItem.FLAG_BROWSABLE)
                }
                items
            }
            ALBUMS_ID -> {
                ensureMetadataIndexes()
                buildCategoryListItems(
                    mediaCacheService.albums(),
                    ALBUM_PREFIX,
                    buildAlbumCounts(mediaCacheService.cachedFiles),
                    resourceIconUri(R.drawable.ic_auto_albums)
                )
            }
            GENRES_ID -> {
                ensureMetadataIndexes()
                buildCategoryListItems(
                    mediaCacheService.genres(),
                    GENRE_PREFIX,
                    buildGenreCounts(mediaCacheService.cachedFiles),
                    resourceIconUri(R.drawable.ic_auto_genres)
                )
            }
            ARTISTS_ID -> {
                ensureMetadataIndexes()
                buildCategoryListItems(
                    buildLetterBuckets(mediaCacheService.artists()),
                    ARTIST_LETTER_PREFIX,
                    iconUri = resourceIconUri(R.drawable.ic_auto_artists)
                )
            }
            DECADES_ID -> {
                ensureMetadataIndexes()
                buildCategoryListItems(
                    mediaCacheService.decades(),
                    DECADE_PREFIX,
                    buildDecadeCounts(mediaCacheService.cachedFiles),
                    resourceIconUri(R.drawable.ic_auto_decades)
                )
            }
            SEARCH_ID -> {
                mutableListOf(
                    MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId("search_hint")
                            .setTitle("Use the search icon to search")
                            .build(),
                        MediaItem.FLAG_BROWSABLE
                    )
                )
            }
            else -> mutableListOf()
        }
        val elapsed = SystemClock.elapsedRealtime() - start
        Log.d("MyMusicService", "buildMediaItems($parentId) -> ${items.size} in ${elapsed}ms")
        return items
    }

    private fun buildLetterBuckets(values: List<String>): List<String> {
        val letters = mutableSetOf<String>()
        var hasOther = false
        for (value in values) {
            val first = value.trim().firstOrNull()?.uppercaseChar()
            if (first != null && first in 'A'..'Z') {
                letters.add(first.toString())
            } else {
                hasOther = true
            }
        }
        val result = letters.toMutableList()
        result.sort()
        if (hasOther) result.add("#")
        return result
    }

    private fun filterByLetter(values: List<String>, letter: String): List<String> {
        if (letter == "#") {
            return values.filter {
                val first = it.trim().firstOrNull()?.uppercaseChar()
                first == null || first !in 'A'..'Z'
            }.sorted()
        }
        val target = letter.firstOrNull()?.uppercaseChar() ?: return emptyList()
        return values.filter {
            it.trim().firstOrNull()?.uppercaseChar() == target
        }.sorted()
    }

    private fun filterSongsByLetter(
        songs: List<MediaFileInfo>,
        letter: String
    ): List<MediaFileInfo> {
        val target = letter.firstOrNull()?.uppercaseChar()
        return songs.filter { file ->
            val title = file.cleanTitle.trim()
            val first = title.firstOrNull()?.uppercaseChar()
            if (letter == "#") {
                first == null || first !in 'A'..'Z'
            } else {
                target != null && first == target
            }
        }
    }

    private fun buildAlbumCounts(files: List<MediaFileInfo>): Map<String, Int> =
        files.groupingBy { file ->
            file.album?.ifBlank { null } ?: "Unknown Album"
        }.eachCount()

    private fun buildArtistCounts(files: List<MediaFileInfo>): Map<String, Int> =
        files.groupingBy { file ->
            file.artist?.ifBlank { null } ?: "Unknown Artist"
        }.eachCount()

    private fun buildGenreCounts(files: List<MediaFileInfo>): Map<String, Int> =
        files.groupingBy { file ->
            bucketGenre(file.genre)
        }.eachCount()

    @VisibleForTesting
    internal fun buildSongLetterBuckets(songs: List<MediaFileInfo>): List<String> {
        val letters = mutableSetOf<String>()
        var hasOther = false
        for (song in songs) {
            val first = song.cleanTitle.trim().firstOrNull()?.uppercaseChar()
            if (first != null && first in 'A'..'Z') {
                letters.add(first.toString())
            } else {
                hasOther = true
            }
        }
        val result = letters.toMutableList()
        result.sort()
        if (hasOther) result.add("#")
        return result
    }

    @VisibleForTesting
    internal fun buildSongLetterCounts(songs: List<MediaFileInfo>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (song in songs) {
            val title = song.cleanTitle.trim()
            val first = title.firstOrNull()?.uppercaseChar()
            val key = if (first != null && first in 'A'..'Z') first.toString() else "#"
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts
    }

    private fun buildPlayAllShuffleItems(listKey: String): MutableList<MediaItem> {
        val items = mutableListOf<MediaItem>()
        items.add(
            MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(ACTION_PLAY_ALL_PREFIX + listKey)
                    .setTitle("[Play All]")
                    .build(),
                MediaItem.FLAG_PLAYABLE
            )
        )
        items.add(
            MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(ACTION_SHUFFLE_PREFIX + listKey)
                    .setTitle("[Shuffle]")
                    .build(),
                MediaItem.FLAG_PLAYABLE
            )
        )
        return items
    }

    private fun buildSongBucketItems(
        songs: List<MediaFileInfo>,
        listKey: String,
        bucketPrefix: String
    ): MutableList<MediaItem> {
        val items = buildPlayAllShuffleItems(listKey)
        items += buildCategoryListItems(
            buildSongLetterBuckets(songs),
            bucketPrefix,
            buildSongLetterCounts(songs),
            resourceIconUri(R.drawable.ic_auto_song)
        )
        return items
    }

    private fun buildSongLetterItems(
        listKey: String,
        songs: List<MediaFileInfo>
    ): MutableList<MediaItem> {
        val items = buildPlayAllShuffleItems(listKey)
        items += buildSongItems(songs, resourceIconUri(R.drawable.ic_album_placeholder))
        return items
    }

    @VisibleForTesting
    internal fun parseBucketParts(value: String, prefix: String): Pair<String, String>? {
        if (!value.startsWith(prefix)) return null
        val payload = value.removePrefix(prefix)
        val parts = payload.split(":", limit = 2)
        if (parts.size < 2) return null
        return Uri.decode(parts[0]) to Uri.decode(parts[1])
    }

    @VisibleForTesting
    internal fun formatBucketTitle(value: String, prefix: String): String? {
        val parts = parseBucketParts(value, prefix) ?: return null
        return "${parts.first} • ${parts.second}"
    }

    @VisibleForTesting
    internal fun shouldBucketSongs(count: Int): Boolean =
        count > SONG_BUCKET_THRESHOLD

    private fun buildDecadeCounts(files: List<MediaFileInfo>): Map<String, Int> =
        files.groupingBy { file ->
            decadeLabelForYear(file.year)
        }.eachCount()

    private fun decadeLabelForYear(year: Int?): String {
        if (year == null || year <= 0) return "Unknown Decade"
        val decade = (year / 10) * 10
        return "${decade}s"
    }

    private fun loadCachedTreeIfAvailable() {
        if (isScanning) return
        if (mediaCacheService.cachedFiles.isNotEmpty()) return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
        val uri = Uri.parse(uriString)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPermission) return

        serviceScope.launch {
            val persisted = mediaCacheService.loadPersistedCache(this@MyMusicService, uri, limit)
            if (persisted != null) {
                mediaCacheService.buildAlbumArtistIndexesFromCache()
                deliverPendingResults()
                notifyChildrenChanged(ROOT_ID)
                notifyChildrenChanged(HOME_ID)
                notifyChildrenChanged(SONGS_ID)
                notifyChildrenChanged(PLAYLISTS_ID)
                notifyChildrenChanged(ALBUMS_ID)
                notifyChildrenChanged(GENRES_ID)
                notifyChildrenChanged(ARTISTS_ID)
                notifyChildrenChanged(DECADES_ID)
                return@launch
            }
            isScanning = true
            try {
                var lastNotify = 0
                mediaCacheService.scanDirectory(this@MyMusicService, uri, limit) { songsFound, _ ->
                    if (songsFound - lastNotify >= 200) {
                        lastNotify = songsFound
                        notifyChildrenChanged(SONGS_ALL_ID)
                    }
                }
                mediaCacheService.enrichGenresFromMediaStore(this@MyMusicService)
                mediaCacheService.buildAlbumArtistIndexesFromCache()
                mediaCacheService.persistCache(this@MyMusicService, uri, limit)
            } finally {
                isScanning = false
                deliverPendingResults()
                notifyChildrenChanged(ROOT_ID)
                notifyChildrenChanged(HOME_ID)
                notifyChildrenChanged(SONGS_ID)
                notifyChildrenChanged(PLAYLISTS_ID)
                notifyChildrenChanged(ALBUMS_ID)
                notifyChildrenChanged(GENRES_ID)
                notifyChildrenChanged(ARTISTS_ID)
                notifyChildrenChanged(DECADES_ID)
            }
        }
    }

    private fun deliverPendingResults() {
        val pending: Map<String, List<MediaBrowserServiceCompat.Result<MutableList<MediaItem>>>> =
            synchronized(pendingResults) {
                val copy = pendingResults.mapValues { it.value.toList() }
                pendingResults.clear()
                copy
            }

        for ((parentId, results) in pending) {
            val items = buildMediaItems(parentId)
            for (result in results) {
                result.sendResult(items)
            }
        }
    }

    private fun needsMetadataIndexes(parentId: String): Boolean =
        parentId == ALBUMS_ID || parentId == GENRES_ID ||
            parentId == ARTISTS_ID || parentId == DECADES_ID ||
            parentId.startsWith(ALBUM_PREFIX) || parentId.startsWith(GENRE_PREFIX) ||
            parentId.startsWith(ARTIST_PREFIX) || parentId.startsWith(DECADE_PREFIX) ||
            parentId.startsWith(ARTIST_LETTER_PREFIX) || parentId.startsWith(GENRE_LETTER_PREFIX) ||
            parentId.startsWith(GENRE_SONG_LETTER_PREFIX) || parentId.startsWith(DECADE_SONG_LETTER_PREFIX)

    internal fun shouldLoadChildrenAsync(parentId: String, hasIndexes: Boolean): Boolean =
        needsMetadataIndexes(parentId) && !hasIndexes

    private fun ensureMetadataIndexes() {
        if (mediaCacheService.hasAlbumArtistIndexes()) return
        val start = SystemClock.elapsedRealtime()
        mediaCacheService.buildAlbumArtistIndexesFromCache()
        val elapsed = SystemClock.elapsedRealtime() - start
        Log.d("MyMusicService", "buildAlbumArtistIndexesFromCache() in ${elapsed}ms")
    }

    private fun enrichFromCache(files: List<MediaFileInfo>): List<MediaFileInfo> {
        if (files.isEmpty()) return files
        val byUri = mediaCacheService.cachedFiles.associateBy { it.uriString }
        return files.map { file -> byUri[file.uriString] ?: file }
    }

    private fun playTrack(fileInfo: MediaFileInfo) {
        clearPendingIntro()
        releaseMediaPlayer()
        if (!requestAudioFocus()) {
            updatePlaybackState(
                PlaybackStateCompat.STATE_ERROR,
                "Could not acquire audio focus"
            )
            return
        }

        currentFileInfo = fileInfo
        currentMediaId = fileInfo.uriString
        savePlaybackSnapshot()

        val player = MediaPlayer()
        try {
            val uri = Uri.parse(fileInfo.uriString)
            player.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@MyMusicService, uri)
                setOnPreparedListener {
                    consecutivePlaybackErrors = 0
                    val pending = pendingResumePositionMs
                    if (pending != null && pending > 0L) {
                        runCatching { seekTo(pending.toInt()) }
                    }
                    pendingResumePositionMs = null
                    unduckIfNeeded()
                    updateMetadata(fileInfo)
                    maybeSpeakTrackIntroThenStart(fileInfo, this)
                }
                setOnCompletionListener {
                    maybeSpeakTrackFinishedThenAdvance(fileInfo)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(
                        "MyMusicService",
                        "MediaPlayer error what=$what extra=$extra for ${fileInfo.displayName}"
                    )
                    releaseMediaPlayer()
                    abandonAudioFocus()
                    handlePlaybackError("Playback error for ${fileInfo.displayName}")
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: SecurityException) {
            Log.e("MyMusicService", "Permission denied for ${fileInfo.displayName}", e)
            try { player.release() } catch (_: Exception) {}
            mediaPlayer = null
            abandonAudioFocus()
            handlePlaybackError("Permission denied: ${fileInfo.displayName}")
        } catch (e: java.io.IOException) {
            Log.e("MyMusicService", "IO error for ${fileInfo.displayName}", e)
            try { player.release() } catch (_: Exception) {}
            mediaPlayer = null
            abandonAudioFocus()
            handlePlaybackError("Cannot read: ${fileInfo.displayName}")
        } catch (e: IllegalArgumentException) {
            Log.e("MyMusicService", "Invalid URI for ${fileInfo.displayName}", e)
            try { player.release() } catch (_: Exception) {}
            mediaPlayer = null
            abandonAudioFocus()
            handlePlaybackError("Invalid file: ${fileInfo.displayName}")
        } catch (e: Exception) {
            Log.e("MyMusicService", "Unexpected error for ${fileInfo.displayName}", e)
            try { player.release() } catch (_: Exception) {}
            mediaPlayer = null
            abandonAudioFocus()
            handlePlaybackError("Cannot play: ${fileInfo.displayName}")
        }
    }

    private fun maybeSpeakTrackIntroThenStart(fileInfo: MediaFileInfo, preparedPlayer: MediaPlayer) {
        val onComplete: () -> Unit = {
            mainHandler.post {
                if (mediaPlayer !== preparedPlayer) return@post
                runCatching {
                    preparedPlayer.start()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    savePlaybackSnapshot()
                }.onFailure { error ->
                    Log.w("MyMusicService", "Failed to start playback after intro", error)
                }
            }
        }
        if (!trackVoiceIntroEnabled) {
            onComplete()
            return
        }
        ensureTextToSpeechInitialized()
        val tts = textToSpeech
        if (!ttsReady || tts == null) {
            onComplete()
            return
        }
        val title = fileInfo.cleanTitle
        val artist = fileInfo.artist?.takeIf { it.isNotBlank() }
        val introText = buildIntroAnnouncement(artist, title).toSpeakableText()
        val utteranceId = "track_intro_${SystemClock.elapsedRealtime()}"
        val action = PendingSpeechAction(utteranceId, onComplete)
        pendingSpeechAction.set(action)
        val result = tts.speak(introText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            if (pendingSpeechAction.compareAndSet(action, null)) {
                onComplete()
            }
        }
    }

    private fun maybeSpeakTrackFinishedThenAdvance(fileInfo: MediaFileInfo) {
        val onComplete: () -> Unit = {
            mainHandler.post {
                onTrackCompleted()
            }
        }
        if (!trackVoiceOutroEnabled) {
            onComplete()
            return
        }
        ensureTextToSpeechInitialized()
        val tts = textToSpeech
        if (!ttsReady || tts == null) {
            onComplete()
            return
        }
        val title = fileInfo.cleanTitle
        val artist = fileInfo.artist?.takeIf { it.isNotBlank() }
        val outroText = buildOutroAnnouncement(artist, title).toSpeakableText()
        val utteranceId = "track_outro_${SystemClock.elapsedRealtime()}"
        val action = PendingSpeechAction(utteranceId, onComplete)
        pendingSpeechAction.set(action)
        val result = tts.speak(outroText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            if (pendingSpeechAction.compareAndSet(action, null)) {
                onComplete()
            }
        }
    }

    private fun buildIntroAnnouncement(artist: String?, title: String): String {
        val templates = listOf(
            "Now playing %s by %s.",
            "Up next, %s from %s.",
            "Here comes %s by %s.",
            "Let's hear %s by %s.",
            "Coming up, %s from %s.",
            "This is %s by %s."
        )
        val soloTemplates = listOf(
            "Now playing %s.",
            "Up next, %s.",
            "Here comes %s.",
            "Let's hear %s.",
            "Coming up, %s.",
            "This is %s."
        )
        val (pickedIndex, pickedTemplate) = pickTemplate(
            templates = if (artist != null) templates else soloTemplates,
            previousIndex = lastIntroTemplateIndex
        )
        lastIntroTemplateIndex = pickedIndex
        return if (artist != null) {
            String.format(java.util.Locale.getDefault(), pickedTemplate, title, artist)
        } else {
            String.format(java.util.Locale.getDefault(), pickedTemplate, title)
        }
    }

    private fun buildOutroAnnouncement(artist: String?, title: String): String {
        val templates = listOf(
            "That was %s by %s.",
            "You just heard %s from %s.",
            "That was %s by %s just now.",
            "We just finished %s by %s.",
            "That wraps up %s from %s.",
            "Recently played: %s by %s."
        )
        val soloTemplates = listOf(
            "That was %s.",
            "You just heard %s.",
            "That was %s just now.",
            "We just finished %s.",
            "That wraps up %s.",
            "Recently played: %s."
        )
        val (pickedIndex, pickedTemplate) = pickTemplate(
            templates = if (artist != null) templates else soloTemplates,
            previousIndex = lastOutroTemplateIndex
        )
        lastOutroTemplateIndex = pickedIndex
        return if (artist != null) {
            String.format(java.util.Locale.getDefault(), pickedTemplate, title, artist)
        } else {
            String.format(java.util.Locale.getDefault(), pickedTemplate, title)
        }
    }

    private fun pickTemplate(templates: List<String>, previousIndex: Int): Pair<Int, String> {
        if (templates.isEmpty()) return -1 to ""
        if (templates.size == 1) return 0 to templates[0]
        var index = kotlin.random.Random.nextInt(templates.size)
        if (index == previousIndex) {
            index = (index + 1 + kotlin.random.Random.nextInt(templates.size - 1)) % templates.size
        }
        return index to templates[index]
    }

    private fun ensureTextToSpeechInitialized() {
        if (ttsReady || ttsInitializing) return
        if (textToSpeech != null && !ttsReady) {
            runCatching { textToSpeech?.shutdown() }
            textToSpeech = null
        }
        ttsInitializing = true
        textToSpeech = TextToSpeech(this) { status ->
            ttsInitializing = false
            if (status != TextToSpeech.SUCCESS) {
                ttsReady = false
                return@TextToSpeech
            }
            val tts = textToSpeech ?: return@TextToSpeech
            val locale = java.util.Locale.getDefault()
            val result = tts.setLanguage(locale)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            if (ttsReady) {
                tts.setSpeechRate(TTS_SPEECH_RATE)
                tts.setPitch(TTS_PITCH)
                selectNaturalVoiceIfAvailable(tts, locale)
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    runPendingSpeechCompletion(utteranceId)
                }

                override fun onError(utteranceId: String?) {
                    runPendingSpeechCompletion(utteranceId)
                }
            })
        }
    }

    private fun runPendingSpeechCompletion(utteranceId: String?) {
        if (utteranceId.isNullOrBlank()) return
        val action = pendingSpeechAction.get() ?: return
        if (action.utteranceId != utteranceId) return
        if (pendingSpeechAction.compareAndSet(action, null)) {
            action.onComplete.invoke()
        }
    }

    private fun selectNaturalVoiceIfAvailable(tts: TextToSpeech, locale: java.util.Locale) {
        val current = tts.voice
        val candidates = tts.voices?.filter { voice ->
            val vLocale = voice.locale ?: return@filter false
            if (!vLocale.language.equals(locale.language, ignoreCase = true)) return@filter false
            if (voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) {
                return@filter false
            }
            true
        }.orEmpty()
        if (candidates.isEmpty()) return
        val best = candidates.maxWithOrNull(
            compareBy<android.speech.tts.Voice>({ it.quality }, { -it.latency })
        ) ?: return
        if (current?.name == best.name) return
        runCatching { tts.voice = best }
    }

    private fun String.toSpeakableText(): String {
        return this
            .replace("&", " and ")
            .replace("+", " plus ")
            .replace("@", " at ")
            .replace("#", " number ")
            .replace("/", " ")
            .replace("_", " ")
            .replace(Regex("[-–—]"), " ")
            .replace(Regex("[\\[\\]{}()<>\"'`~^*|\\\\]"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s.,!?]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun clearPendingIntro() {
        pendingSpeechAction.set(null)
        runCatching { textToSpeech?.stop() }
    }

    internal fun advanceQueueOnError(): Boolean {
        val nextIndex = nextQueueIndexForError(currentQueueIndex, playlistQueue.size)
        return if (nextIndex != null) {
            currentQueueIndex = nextIndex
            true
        } else {
            false
        }
    }

    internal fun handlePlaybackError(message: String) {
        consecutivePlaybackErrors += 1
        if (!shouldRetryPlaybackError(consecutivePlaybackErrors, MAX_CONSECUTIVE_PLAYBACK_ERRORS)) {
            Log.w("MyMusicService", "Too many consecutive playback errors, stopping recovery")
            handleStop()
            consecutivePlaybackErrors = 0
            return
        }
        if (advanceQueueOnError()) {
            Log.d("MyMusicService", "Skipping failed track, advancing to index $currentQueueIndex")
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
            return
        }
        val currentUri = currentFileInfo?.uriString
        if (!currentUri.isNullOrBlank()) {
            val fallbackList = mediaCacheService.cachedFiles
            if (fallbackList.isNotEmpty()) {
                val currentIndex = fallbackList.indexOfFirst { it.uriString == currentUri }
                val nextIndex = if (currentIndex >= 0 && currentIndex < fallbackList.lastIndex) {
                    currentIndex + 1
                } else {
                    -1
                }
                if (nextIndex >= 0) {
                    playlistQueue = fallbackList
                    currentQueueIndex = nextIndex
                    currentPlaylistName = currentPlaylistName ?: "All Songs"
                    updateSessionQueue()
                    playTrack(playlistQueue[currentQueueIndex])
                    return
                }
            }
        }
        Log.w("MyMusicService", "Unable to recover playback error: $message")
        handleStop()
    }

    private fun onTrackCompleted() {
        if (playlistQueue.isNotEmpty()) {
            if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE &&
                currentQueueIndex in playlistQueue.indices
            ) {
                updateSessionQueue()
                playTrack(playlistQueue[currentQueueIndex])
                return
            }
            val nextIndex = currentQueueIndex + 1
            if (nextIndex < playlistQueue.size) {
                currentQueueIndex = nextIndex
                updateSessionQueue()
                playTrack(playlistQueue[currentQueueIndex])
            } else if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
                currentQueueIndex = 0
                updateSessionQueue()
                playTrack(playlistQueue[currentQueueIndex])
            } else {
                handleStop()
            }
        } else {
            handleStop()
        }
        savePlaybackSnapshot(positionMsOverride = 0L)
    }

    private fun updateSessionQueue() {
        if (playlistQueue.isEmpty()) {
            session.setQueue(null)
            session.setQueueTitle(null)
            savePlaybackSnapshot()
            return
        }

        val queueItems = playlistQueue.mapIndexed { index, fileInfo ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(fileInfo.uriString)
                .setTitle(fileInfo.cleanTitle)
                .setSubtitle(fileInfo.artist)
                .build()
            MediaSessionCompat.QueueItem(description, index.toLong())
        }
        session.setQueue(queueItems)
        session.setQueueTitle(currentPlaylistName)
        savePlaybackSnapshot()
    }


    private fun requestAudioFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        audioFocusRequest = request
        val result = audioManager.requestAudioFocus(request)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun handlePlayFromMediaId(resolvedMediaId: String) {
        if (resolvedMediaId.startsWith(ACTION_PLAY_ALL_PREFIX) ||
            resolvedMediaId.startsWith(ACTION_SHUFFLE_PREFIX)
        ) {
            val isShuffle = resolvedMediaId.startsWith(ACTION_SHUFFLE_PREFIX)
            val listKey = resolvedMediaId.removePrefix(
                if (isShuffle) ACTION_SHUFFLE_PREFIX else ACTION_PLAY_ALL_PREFIX
            )
            val tracks = when {
                listKey == SONGS_ID -> mediaCacheService.cachedFiles
                listKey == PLAYLISTS_ID -> {
                    val all = mutableListOf<MediaFileInfo>()
                    for (playlist in mediaCacheService.discoveredPlaylists) {
                        all += playlistService.readPlaylist(
                            this,
                            Uri.parse(playlist.uriString)
                        )
                    }
                    enrichFromCache(all)
                }
                listKey.startsWith(PLAYLIST_URI_PREFIX) -> {
                    val playlistUri =
                        Uri.decode(listKey.removePrefix(PLAYLIST_URI_PREFIX))
                    enrichFromCache(
                        playlistService.readPlaylist(
                            this,
                            Uri.parse(playlistUri)
                        )
                    )
                }
                listKey.startsWith(SMART_PLAYLIST_PREFIX) -> {
                    val smartId = Uri.decode(listKey.removePrefix(SMART_PLAYLIST_PREFIX))
                    resolveSmartPlaylistTracksById(smartId) ?: emptyList()
                }
                listKey.startsWith(ALBUM_PREFIX) -> {
                    ensureMetadataIndexes()
                    val album = Uri.decode(listKey.removePrefix(ALBUM_PREFIX))
                    mediaCacheService.songsForAlbum(album)
                }
                listKey.startsWith(GENRE_PREFIX) -> {
                    ensureMetadataIndexes()
                    val genre = Uri.decode(listKey.removePrefix(GENRE_PREFIX))
                    mediaCacheService.songsForGenre(genre)
                }
                listKey.startsWith(ARTIST_PREFIX) -> {
                    ensureMetadataIndexes()
                    val artist = Uri.decode(listKey.removePrefix(ARTIST_PREFIX))
                    mediaCacheService.songsForArtist(artist)
                }
                listKey.startsWith(DECADE_PREFIX) -> {
                    ensureMetadataIndexes()
                    val decade = Uri.decode(listKey.removePrefix(DECADE_PREFIX))
                    mediaCacheService.songsForDecade(decade)
                }
                listKey.startsWith(SONG_LETTER_PREFIX) -> {
                    val letter = Uri.decode(listKey.removePrefix(SONG_LETTER_PREFIX))
                    filterSongsByLetter(mediaCacheService.cachedFiles, letter)
                }
                listKey.startsWith(GENRE_SONG_LETTER_PREFIX) -> {
                    ensureMetadataIndexes()
                    val parts = parseBucketParts(listKey, GENRE_SONG_LETTER_PREFIX)
                    if (parts == null) emptyList() else {
                        filterSongsByLetter(mediaCacheService.songsForGenre(parts.first), parts.second)
                    }
                }
                listKey.startsWith(DECADE_SONG_LETTER_PREFIX) -> {
                    ensureMetadataIndexes()
                    val parts = parseBucketParts(listKey, DECADE_SONG_LETTER_PREFIX)
                    if (parts == null) emptyList() else {
                        filterSongsByLetter(mediaCacheService.songsForDecade(parts.first), parts.second)
                    }
                }
                else -> emptyList()
            }

            if (tracks.isEmpty()) {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                return
            }

            val ordered = if (isShuffle) tracks.shuffled() else tracks
            playlistQueue = ordered
            currentQueueIndex = 0
            currentPlaylistName = when {
                listKey == SONGS_ID -> "All Songs"
                listKey == PLAYLISTS_ID -> "All Playlists"
                listKey.startsWith(PLAYLIST_URI_PREFIX) -> {
                    Uri.decode(listKey.removePrefix(PLAYLIST_URI_PREFIX))
                }
                listKey.startsWith(SMART_PLAYLIST_PREFIX) -> {
                    val smartId = Uri.decode(listKey.removePrefix(SMART_PLAYLIST_PREFIX))
                    smartPlaylistTitleFromId(smartId)
                }
                listKey.startsWith(ALBUM_PREFIX) ->
                    Uri.decode(listKey.removePrefix(ALBUM_PREFIX))
                listKey.startsWith(GENRE_PREFIX) ->
                    Uri.decode(listKey.removePrefix(GENRE_PREFIX))
                listKey.startsWith(ARTIST_PREFIX) ->
                    Uri.decode(listKey.removePrefix(ARTIST_PREFIX))
                listKey.startsWith(DECADE_PREFIX) ->
                    Uri.decode(listKey.removePrefix(DECADE_PREFIX))
                listKey.startsWith(SONG_LETTER_PREFIX) -> {
                    val letter = Uri.decode(listKey.removePrefix(SONG_LETTER_PREFIX))
                    "Songs $letter"
                }
                listKey.startsWith(GENRE_SONG_LETTER_PREFIX) -> {
                    formatBucketTitle(listKey, GENRE_SONG_LETTER_PREFIX) ?: "Playlist"
                }
                listKey.startsWith(DECADE_SONG_LETTER_PREFIX) -> {
                    formatBucketTitle(listKey, DECADE_SONG_LETTER_PREFIX) ?: "Playlist"
                }
                else -> "Playlist"
            }
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
            savePlaybackSnapshot()
            return
        }
        if (resolvedMediaId.startsWith(PLAYLIST_PREFIX)) {
            val playlistUri = Uri.decode(resolvedMediaId.removePrefix(PLAYLIST_PREFIX))
            val playlistInfo = mediaCacheService.discoveredPlaylists.firstOrNull {
                it.uriString == playlistUri
            } ?: return
            val playlistTracks = enrichFromCache(
                playlistService.readPlaylist(
                    this,
                    Uri.parse(playlistInfo.uriString)
                )
            )
            if (playlistTracks.isEmpty()) {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                return
            }
            playlistQueue = playlistTracks
            currentQueueIndex = 0
            currentPlaylistName = playlistInfo.displayName.removeSuffix(".m3u")
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
            savePlaybackSnapshot()
            return
        }
        if (resolvedMediaId.startsWith(SMART_PLAYLIST_PREFIX)) {
            val smartId = Uri.decode(resolvedMediaId.removePrefix(SMART_PLAYLIST_PREFIX))
            val playlistTracks = resolveSmartPlaylistTracksById(smartId) ?: emptyList()
            if (playlistTracks.isEmpty()) {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                return
            }
            playlistQueue = playlistTracks
            currentQueueIndex = 0
            currentPlaylistName = smartPlaylistTitleFromId(smartId)
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
            savePlaybackSnapshot()
            return
        }

        val fileInfo = mediaCacheService.cachedFiles.firstOrNull {
            it.uriString == resolvedMediaId
        } ?: MediaFileInfo(
            uriString = resolvedMediaId,
            displayName = Uri.parse(resolvedMediaId).lastPathSegment ?: resolvedMediaId,
            sizeBytes = 0L,
            title = Uri.parse(resolvedMediaId).lastPathSegment
        )

        val parentId = lastBrowseParentId
        val listFromParent = when {
            parentId == SONGS_ID -> mediaCacheService.cachedFiles
            parentId == SONGS_ALL_ID -> mediaCacheService.cachedFiles
            parentId != null && parentId.startsWith(ALBUM_PREFIX) -> {
                ensureMetadataIndexes()
                val album = Uri.decode(parentId.removePrefix(ALBUM_PREFIX))
                mediaCacheService.songsForAlbum(album)
            }
            parentId != null && parentId.startsWith(GENRE_PREFIX) -> {
                ensureMetadataIndexes()
                val genre = Uri.decode(parentId.removePrefix(GENRE_PREFIX))
                mediaCacheService.songsForGenre(genre)
            }
            parentId != null && parentId.startsWith(ARTIST_PREFIX) -> {
                ensureMetadataIndexes()
                val artist = Uri.decode(parentId.removePrefix(ARTIST_PREFIX))
                mediaCacheService.songsForArtist(artist)
            }
            parentId != null && parentId.startsWith(DECADE_PREFIX) -> {
                ensureMetadataIndexes()
                val decade = Uri.decode(parentId.removePrefix(DECADE_PREFIX))
                mediaCacheService.songsForDecade(decade)
            }
            parentId != null && parentId.startsWith(SONG_LETTER_PREFIX) -> {
                val letter = Uri.decode(parentId.removePrefix(SONG_LETTER_PREFIX))
                filterSongsByLetter(mediaCacheService.cachedFiles, letter)
            }
            parentId != null && parentId.startsWith(GENRE_SONG_LETTER_PREFIX) -> {
                ensureMetadataIndexes()
                val parts = parseBucketParts(parentId, GENRE_SONG_LETTER_PREFIX)
                if (parts == null) emptyList() else {
                    filterSongsByLetter(mediaCacheService.songsForGenre(parts.first), parts.second)
                }
            }
            parentId != null && parentId.startsWith(DECADE_SONG_LETTER_PREFIX) -> {
                ensureMetadataIndexes()
                val parts = parseBucketParts(parentId, DECADE_SONG_LETTER_PREFIX)
                if (parts == null) emptyList() else {
                    filterSongsByLetter(mediaCacheService.songsForDecade(parts.first), parts.second)
                }
            }
            else -> emptyList()
        }

        val searchList = if (lastSearchResults.any { it.uriString == resolvedMediaId }) {
            lastSearchResults
        } else {
            emptyList()
        }
        val parentList = when {
            listFromParent.isNotEmpty() -> listFromParent
            searchList.isNotEmpty() -> searchList
            mediaCacheService.cachedFiles.isNotEmpty() -> mediaCacheService.cachedFiles
            else -> emptyList()
        }

        if (parentList.isNotEmpty()) {
            playlistQueue = parentList
            currentQueueIndex = parentList.indexOfFirst { it.uriString == resolvedMediaId }
            if (currentQueueIndex < 0) {
                playlistQueue = emptyList()
                currentQueueIndex = -1
                currentPlaylistName = null
            } else {
                currentPlaylistName = when {
                    searchList.isNotEmpty() -> {
                        val query = lastSearchQuery?.trim().orEmpty()
                        if (query.isNotEmpty()) "Search: $query" else "Search Results"
                    }
                    parentId == SONGS_ID || parentId == SONGS_ALL_ID || listFromParent.isEmpty() -> "All Songs"
                    parentId?.startsWith(ALBUM_PREFIX) == true ->
                        Uri.decode(parentId.removePrefix(ALBUM_PREFIX))
                    parentId?.startsWith(GENRE_PREFIX) == true ->
                        Uri.decode(parentId.removePrefix(GENRE_PREFIX))
                    parentId?.startsWith(ARTIST_PREFIX) == true ->
                        Uri.decode(parentId.removePrefix(ARTIST_PREFIX))
                    parentId?.startsWith(DECADE_PREFIX) == true ->
                        Uri.decode(parentId.removePrefix(DECADE_PREFIX))
                    parentId?.startsWith(SONG_LETTER_PREFIX) == true -> {
                        val letter = Uri.decode(parentId.removePrefix(SONG_LETTER_PREFIX))
                        "Songs $letter"
                    }
                    parentId?.startsWith(GENRE_SONG_LETTER_PREFIX) == true -> {
                        formatBucketTitle(parentId, GENRE_SONG_LETTER_PREFIX)
                    }
                    parentId?.startsWith(DECADE_SONG_LETTER_PREFIX) == true -> {
                        formatBucketTitle(parentId, DECADE_SONG_LETTER_PREFIX)
                    }
                    else -> null
                }
            }
        } else {
            playlistQueue = emptyList()
            currentQueueIndex = -1
            currentPlaylistName = null
        }

        updateSessionQueue()
        playTrack(fileInfo)
        savePlaybackSnapshot()
    }

    private suspend fun handlePlayFromSearch(query: String?, extras: Bundle?) {
        ensureCacheReadyForSearch()
        val raw = query?.trim().orEmpty()
        val lowered = raw.lowercase()
        val wantsShuffle = Regex("\\bshuffle\\b").containsMatchIn(lowered)
        var cleanedQuery = lowered.trim()
        while (true) {
            val next = cleanedQuery
                .replaceFirst(Regex("^\\s*(play|shuffle)\\b\\s*"), "")
                .trim()
            if (next == cleanedQuery) break
            cleanedQuery = next
        }

        val mediaFocus = extras?.getString(EXTRA_MEDIA_FOCUS_KEY)?.trim().orEmpty()
        val requestedPlaylist = extras?.getString(EXTRA_MEDIA_PLAYLIST_KEY)?.trim().orEmpty()
        val requestedArtist = extras?.getString(EXTRA_MEDIA_ARTIST_KEY)?.trim().orEmpty()
        val requestedAlbum = extras?.getString(EXTRA_MEDIA_ALBUM_KEY)?.trim().orEmpty()
        val requestedGenre = extras?.getString(EXTRA_MEDIA_GENRE_KEY)?.trim().orEmpty()
        val requestedTitle = extras?.getString(EXTRA_MEDIA_TITLE_KEY)?.trim().orEmpty()
        val focusPlaylist = mediaFocus.equals(FOCUS_PLAYLIST, ignoreCase = true)
        val focusArtist = mediaFocus.equals(FOCUS_ARTIST, ignoreCase = true)
        val focusAlbum = mediaFocus.equals(FOCUS_ALBUM, ignoreCase = true)
        val focusGenre = mediaFocus.equals(FOCUS_GENRE, ignoreCase = true)
        val focusTitle = mediaFocus.equals(FOCUS_TITLE, ignoreCase = true)

        val playlistNameQuery = when {
            requestedPlaylist.isNotBlank() -> requestedPlaylist
            focusPlaylist && cleanedQuery.isNotBlank() -> cleanedQuery
            else -> ""
        }
        val smartPlaylistQuery = when {
            playlistNameQuery.isNotBlank() -> playlistNameQuery
            else -> cleanedQuery
        }
        val smartPlaylistId = smartPlaylistIdFromQuery(smartPlaylistQuery)
        if (smartPlaylistId != null) {
            val smartTracks = resolveSmartPlaylistTracksById(smartPlaylistId)
            if (smartTracks != null && smartTracks.isNotEmpty()) {
                playlistQueue = if (wantsShuffle) smartTracks.shuffled() else smartTracks
                currentQueueIndex = 0
                currentPlaylistName = smartPlaylistTitleFromId(smartPlaylistId)
                updateSessionQueue()
                playTrack(playlistQueue[currentQueueIndex])
                savePlaybackSnapshot()
                return
            }
        }
        if (playlistNameQuery.isNotBlank()) {
            val playlist = mediaCacheService.discoveredPlaylists.firstOrNull {
                it.displayName.removeSuffix(".m3u").contains(playlistNameQuery, ignoreCase = true)
            }
            if (playlist != null) {
                val tracks = enrichFromCache(
                    playlistService.readPlaylist(this, Uri.parse(playlist.uriString))
                )
                if (tracks.isNotEmpty()) {
                    playlistQueue = if (wantsShuffle) tracks.shuffled() else tracks
                    currentQueueIndex = 0
                    currentPlaylistName = playlist.displayName.removeSuffix(".m3u")
                    updateSessionQueue()
                    playTrack(playlistQueue[currentQueueIndex])
                    savePlaybackSnapshot()
                    return
                }
            }
        }

        fun refine(
            base: List<MediaFileInfo>?,
            filter: (MediaFileInfo) -> Boolean
        ): List<MediaFileInfo> {
            val source = base ?: mediaCacheService.cachedFiles
            return source.filter(filter)
        }

        var focusedMatches: List<MediaFileInfo>? = null
        var focusLabel: String? = null
        val focusQuery = cleanedQuery.ifBlank { raw }.trim()
        if (requestedArtist.isNotBlank()) {
            focusedMatches = refine(focusedMatches) { file ->
                file.artist?.contains(requestedArtist, ignoreCase = true) == true
            }
            focusLabel = "Artist: $requestedArtist"
        }
        if (requestedAlbum.isNotBlank()) {
            focusedMatches = refine(focusedMatches) { file ->
                file.album?.contains(requestedAlbum, ignoreCase = true) == true
            }
            focusLabel = "Album: $requestedAlbum"
        }
        if (requestedGenre.isNotBlank()) {
            focusedMatches = refine(focusedMatches) { file ->
                file.genre?.contains(requestedGenre, ignoreCase = true) == true
            }
            focusLabel = "Genre: $requestedGenre"
        }
        if (requestedTitle.isNotBlank()) {
            focusedMatches = refine(focusedMatches) { file ->
                file.cleanTitle.contains(requestedTitle, ignoreCase = true)
            }
            focusLabel = "Title: $requestedTitle"
        }

        if (focusedMatches == null && focusQuery.isNotBlank()) {
            focusedMatches = when {
                focusArtist -> mediaCacheService.cachedFiles.filter { file ->
                    file.artist?.contains(focusQuery, ignoreCase = true) == true
                }
                focusAlbum -> mediaCacheService.cachedFiles.filter { file ->
                    file.album?.contains(focusQuery, ignoreCase = true) == true
                }
                focusGenre -> mediaCacheService.cachedFiles.filter { file ->
                    file.genre?.contains(focusQuery, ignoreCase = true) == true
                }
                focusTitle -> mediaCacheService.cachedFiles.filter { file ->
                    file.cleanTitle.contains(focusQuery, ignoreCase = true)
                }
                else -> null
            }
            focusLabel = when {
                focusArtist -> "Artist: $focusQuery"
                focusAlbum -> "Album: $focusQuery"
                focusGenre -> "Genre: $focusQuery"
                focusTitle -> "Title: $focusQuery"
                else -> focusLabel
            }
        }

        val queryForSongs = cleanedQuery.ifBlank { raw }.trim()
        val matches = when {
            focusedMatches != null && focusedMatches.isNotEmpty() -> focusedMatches
            queryForSongs.isBlank() -> mediaCacheService.cachedFiles
            else -> mediaCacheService.searchFiles(queryForSongs)
        }
        if (matches.isEmpty()) {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR, "No results for \"$raw\"")
            return
        }

        playlistQueue = if (wantsShuffle) matches.shuffled() else matches
        currentQueueIndex = 0
        currentPlaylistName = when {
            focusLabel != null -> focusLabel
            queryForSongs.isBlank() -> {
                "All Songs"
            }
            else -> {
                "Search: $queryForSongs"
            }
        }
        updateSessionQueue()
        playTrack(playlistQueue[currentQueueIndex])
        savePlaybackSnapshot()
    }

    private suspend fun ensureCacheReadyForSearch() {
        if (mediaCacheService.cachedFiles.isNotEmpty()) return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
        val uri = Uri.parse(uriString)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPermission) return
        mediaCacheService.loadPersistedCache(this, uri, limit)
    }

    private fun resolveSmartPlaylistTitle(playlistNameQuery: String): String {
        val smartId = smartPlaylistIdFromQuery(playlistNameQuery) ?: return playlistNameQuery
        return smartPlaylistTitleFromId(smartId)
    }

    private fun resolveSmartPlaylistTracks(playlistNameQuery: String): List<MediaFileInfo>? {
        val smartId = smartPlaylistIdFromQuery(playlistNameQuery) ?: return null
        return resolveSmartPlaylistTracksById(smartId)
    }

    @VisibleForTesting
    internal fun smartPlaylistIdFromQuery(query: String): String? {
        val needle = query.lowercase().trim()
        if (needle.isBlank()) return null
        return when {
            needle.contains("favorite") -> SMART_PLAYLIST_FAVORITES
            needle.contains("recently added") || needle == "recent" || needle.contains(" recent") ->
                SMART_PLAYLIST_RECENTLY_ADDED
            needle.contains("most played") || (needle.contains("most") && needle.contains("played")) ->
                SMART_PLAYLIST_MOST_PLAYED
            needle.contains("haven't heard") ||
                needle.contains("havent heard") ||
                needle.contains("not heard") ||
                needle.contains("unheard") -> SMART_PLAYLIST_NOT_HEARD_RECENTLY
            else -> null
        }
    }

    private fun smartPlaylistTitleFromId(smartId: String): String {
        return when (smartId) {
            SMART_PLAYLIST_FAVORITES -> "Favorites"
            SMART_PLAYLIST_RECENTLY_ADDED -> "Recently Added"
            SMART_PLAYLIST_MOST_PLAYED -> "Most Played"
            SMART_PLAYLIST_NOT_HEARD_RECENTLY -> "Haven't Heard In A While"
            else -> smartId
        }
    }

    private fun resolveSmartPlaylistTracksById(smartId: String): List<MediaFileInfo>? {
        val all = mediaCacheService.cachedFiles.ifEmpty { playlistQueue }
        if (all.isEmpty()) return null
        return when (smartId) {
            SMART_PLAYLIST_FAVORITES -> {
                val favorites = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getStringSet(KEY_FAVORITE_URIS, emptySet())
                    ?.toSet()
                    ?: emptySet()
                all.filter { it.uriString in favorites }
            }
            SMART_PLAYLIST_RECENTLY_ADDED -> {
                all.sortedByDescending { it.addedAtMs ?: Long.MIN_VALUE }
            }
            SMART_PLAYLIST_MOST_PLAYED -> {
                val playCounts = parsePlayCounts(
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PLAY_COUNTS, null)
                )
                all.mapNotNull { file ->
                    val plays = playCounts[file.uriString] ?: 0
                    if (plays > 0) file to plays else null
                }.sortedByDescending { it.second }.map { it.first }
            }
            SMART_PLAYLIST_NOT_HEARD_RECENTLY -> {
                val lastPlayedAt = parseLongMap(
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_LAST_PLAYED_AT, null)
                )
                all.sortedWith(
                    compareBy<MediaFileInfo> { lastPlayedAt[it.uriString] != null }
                        .thenBy { lastPlayedAt[it.uriString] ?: Long.MIN_VALUE }
                        .thenBy { it.cleanTitle.lowercase() }
                )
            }
            else -> null
        }
    }

    private fun parsePlayCounts(raw: String?): Map<String, Int> {
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

    private fun parseLongMap(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = mutableMapOf<String, Long>()
        raw.lineSequence().forEach { line ->
            val split = line.split('\t', limit = 2)
            if (split.size != 2) return@forEach
            val key = split[0].trim()
            val value = split[1].trim().toLongOrNull() ?: return@forEach
            if (key.isNotEmpty() && value > 0L) out[key] = value
        }
        return out
    }

    private fun abandonAudioFocus() {
        resumeOnAudioFocusGain = false
        unduckIfNeeded()
        val request = audioFocusRequest ?: return
        audioManager.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        mediaPlayer = null
    }

    private fun handlePause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            savePlaybackSnapshot()
        }
    }

    private fun handleStop() {
        resumeOnAudioFocusGain = false
        val lastPosition = currentPositionSafeMs()
        releaseMediaPlayer()
        abandonAudioFocus()
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        consecutivePlaybackErrors = 0
        savePlaybackSnapshot(positionMsOverride = lastPosition)
    }

    private fun updatePlaybackState(state: Int, errorMessage: String? = null) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val speed = if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f
        val playbackActions = resolvePlaybackActions(
            state = state,
            queueSize = playlistQueue.size,
            queueIndex = currentQueueIndex,
            canSeek = mediaPlayer != null
        )

        playbackStateBuilder
            .setActions(playbackActions)
            .setState(state, position, speed, SystemClock.elapsedRealtime())

        if (state == PlaybackStateCompat.STATE_ERROR && errorMessage != null) {
            playbackStateBuilder.setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                errorMessage
            )
        } else {
            playbackStateBuilder.setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
                null
            )
        }

        if (playlistQueue.isNotEmpty() && currentQueueIndex >= 0) {
            playbackStateBuilder.setActiveQueueItemId(currentQueueIndex.toLong())
        } else {
            playbackStateBuilder.setActiveQueueItemId(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN)
        }

        session.setPlaybackState(playbackStateBuilder.build())
        updateNowPlayingNotification(state)
    }

    @VisibleForTesting
    internal fun resolvePlaybackActions(
        state: Int,
        queueSize: Int,
        queueIndex: Int,
        canSeek: Boolean
    ): Long {
        val baseActions = when (state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP
            }
            PlaybackStateCompat.STATE_STOPPED -> {
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            }
            else -> {
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            }
        }

        val queueActions = if (queueSize > 1 && queueIndex >= 0) {
            var actions = 0L
            if (queueIndex < queueSize - 1) {
                actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            }
            if (queueIndex > 0) {
                actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            }
            actions or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
        } else {
            0L
        }

        val seekActions = if (canSeek) PlaybackStateCompat.ACTION_SEEK_TO else 0L
        val repeatActions = PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        return baseActions or queueActions or seekActions or repeatActions
    }

    private fun updateMetadata(fileInfo: MediaFileInfo) {
        val runtimeMetadata = MediaMetadataHelper.extractMetadata(this, fileInfo.uriString)
        val title = runtimeMetadata?.title?.takeIf { it.isNotBlank() }
            ?: fileInfo.cleanTitle
        val artist = runtimeMetadata?.artist ?: fileInfo.artist
        val album = runtimeMetadata?.album ?: fileInfo.album
        val duration = runtimeMetadata?.durationMs?.toLongOrNull() ?: fileInfo.durationMs ?: 0L
        val year = runtimeMetadata?.year?.toLongOrNull() ?: (fileInfo.year ?: 0).toLong()
        val embeddedArtBitmap = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, Uri.parse(fileInfo.uriString))
                val artBytes = retriever.embeddedPicture ?: return@runCatching null
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }.getOrNull()

        val albumArtBitmap = embeddedArtBitmap ?: loadPlaceholderArt()

        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, fileInfo.uriString)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, year)
        albumArtBitmap?.let { bitmap ->
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
        }
        val metadata = builder.build()
        lastMetadata = metadata
        session.setMetadata(metadata)
        updateNowPlayingNotification(lastPlaybackState()?.state ?: PlaybackStateCompat.STATE_NONE)
    }

    private fun loadPlaceholderArt(): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_album_placeholder) ?: return null
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun ensureNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(NOW_PLAYING_CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            NOW_PLAYING_CHANNEL_ID,
            "Now Playing",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun lastPlaybackState(): PlaybackStateCompat? =
        session.controller?.playbackState

    private fun buildNowPlayingNotification(state: Int): android.app.Notification {
        val metadata = lastMetadata ?: session.controller?.metadata
        val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "My Media Player"
        val artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        val art = metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART) ?:
            metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
        val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
        val hasPrev = playlistQueue.size > 1 && currentQueueIndex > 0
        val hasNext = playlistQueue.size > 1 && currentQueueIndex < playlistQueue.size - 1

        val builder = NotificationCompat.Builder(this, NOW_PLAYING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_auto_song)
            .setContentTitle(title)
            .setContentText(artist)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)

        if (art != null) {
            builder.setLargeIcon(art)
        }

        val contentIntent = session.controller?.sessionActivity
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        if (hasPrev) {
            builder.addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                )
            )
        }

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PAUSE
                )
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY
                )
            )
        }
        builder.addAction(playPauseAction)

        if (hasNext) {
            builder.addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                )
            )
        }

        val compactIndices = when {
            hasPrev && hasNext -> intArrayOf(0, 1, 2)
            hasPrev || hasNext -> intArrayOf(0, 1)
            else -> intArrayOf(0)
        }

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(*compactIndices)
        )

        return builder.build()
    }

    private fun buildLaunchIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNowPlayingNotification(state: Int) {
        val notification = buildNowPlayingNotification(state)
        runCatching {
            when (state) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    startForeground(NOW_PLAYING_NOTIFICATION_ID, notification)
                }
                PlaybackStateCompat.STATE_PAUSED -> {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    notificationManager.notify(NOW_PLAYING_NOTIFICATION_ID, notification)
                }
                else -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notificationManager.cancel(NOW_PLAYING_NOTIFICATION_ID)
                }
            }
        }.onFailure {
            Log.w("MyMusicService", "Failed to update now playing notification", it)
        }
    }

    private fun buildHomeItems(): MutableList<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val childListExtras = Bundle().apply {
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
            )
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        val playlistCount = mediaCacheService.discoveredPlaylists.size
        if (playlistCount > 0) {
            items.add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(PLAYLISTS_ID)
                        .setTitle("Playlists")
                        .setSubtitle("$playlistCount playlist${if (playlistCount != 1) "s" else ""}")
                        .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                        .setExtras(childListExtras)
                        .build(),
                    MediaItem.FLAG_BROWSABLE
                )
            )
        }

        val songCount = mediaCacheService.cachedFiles.size
        if (songCount > 0) {
            ensureMetadataIndexes()
            val genreCount = mediaCacheService.genres().size
            val decadeCount = mediaCacheService.decades().size
            val albumCount = mediaCacheService.albums().size
            val artistCount = mediaCacheService.artists().size

            items.add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(GENRES_ID)
                        .setTitle("Genres")
                        .setSubtitle("$genreCount genre${if (genreCount != 1) "s" else ""}")
                        .setIconUri(resourceIconUri(R.drawable.ic_auto_genres))
                        .setExtras(childListExtras)
                        .build(),
                    MediaItem.FLAG_BROWSABLE
                )
            )
            items.add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(DECADES_ID)
                        .setTitle("Decades")
                        .setSubtitle("$decadeCount decade${if (decadeCount != 1) "s" else ""}")
                        .setIconUri(resourceIconUri(R.drawable.ic_auto_decades))
                        .setExtras(childListExtras)
                        .build(),
                    MediaItem.FLAG_BROWSABLE
                )
            )
            items.add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(ALBUMS_ID)
                        .setTitle("Albums")
                        .setSubtitle("$albumCount album${if (albumCount != 1) "s" else ""}")
                        .setIconUri(resourceIconUri(R.drawable.ic_auto_albums))
                        .setExtras(childListExtras)
                        .build(),
                    MediaItem.FLAG_BROWSABLE
                )
            )
            items.add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(ARTISTS_ID)
                        .setTitle("Artists")
                        .setSubtitle("$artistCount artist${if (artistCount != 1) "s" else ""}")
                        .setIconUri(resourceIconUri(R.drawable.ic_auto_artists))
                        .setExtras(childListExtras)
                        .build(),
                    MediaItem.FLAG_BROWSABLE
                )
            )
            items.add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(SONGS_ID)
                        .setTitle("Songs")
                        .setSubtitle("$songCount song${if (songCount != 1) "s" else ""}")
                        .setIconUri(resourceIconUri(R.drawable.ic_auto_song))
                        .setExtras(childListExtras)
                        .build(),
                    MediaItem.FLAG_BROWSABLE
                )
            )
        }
        return items
    }

    private fun restorePlaybackSnapshot() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        repeatMode = prefs.getInt(KEY_RESUME_REPEAT_MODE, PlaybackStateCompat.REPEAT_MODE_NONE)
        session.setRepeatMode(repeatMode)

        val queueUrisRaw = prefs.getString(KEY_RESUME_QUEUE_URIS, null).orEmpty()
        val queueUris = if (queueUrisRaw.isBlank()) {
            emptyList()
        } else {
            queueUrisRaw.split('\n').filter { it.isNotBlank() }
        }
        if (queueUris.isNotEmpty()) {
            val byUri = mediaCacheService.cachedFiles.associateBy { it.uriString }
            playlistQueue = queueUris.map { uri ->
                byUri[uri] ?: MediaFileInfo(
                    uriString = uri,
                    displayName = uri,
                    sizeBytes = 0L,
                    title = uri
                )
            }
            val savedIndex = prefs.getInt(KEY_RESUME_QUEUE_INDEX, -1)
            val clampedIndex = savedIndex.coerceIn(-1, playlistQueue.lastIndex)
            currentQueueIndex = if (clampedIndex >= 0) clampedIndex else 0
            currentPlaylistName = prefs.getString(KEY_RESUME_QUEUE_TITLE, null)
            updateSessionQueue()
        } else {
            playlistQueue = emptyList()
            currentQueueIndex = -1
            currentPlaylistName = null
            updateSessionQueue()
        }

        val savedMediaUri = prefs.getString(KEY_RESUME_MEDIA_URI, null)
        if (!savedMediaUri.isNullOrBlank()) {
            currentMediaId = savedMediaUri
            currentFileInfo = playlistQueue.firstOrNull { it.uriString == savedMediaUri }
                ?: mediaCacheService.cachedFiles.firstOrNull { it.uriString == savedMediaUri }
                ?: MediaFileInfo(
                    uriString = savedMediaUri,
                    displayName = savedMediaUri,
                    sizeBytes = 0L,
                    title = savedMediaUri
                )
            if (currentQueueIndex < 0 && playlistQueue.isNotEmpty()) {
                currentQueueIndex = playlistQueue.indexOfFirst { it.uriString == savedMediaUri }
            }
        }

        val savedPosition = prefs.getLong(KEY_RESUME_POSITION_MS, 0L)
        pendingResumePositionMs = if (savedPosition > 0L) savedPosition else null
    }

    private fun savePlaybackSnapshot(positionMsOverride: Long? = null) {
        val currentUri = currentMediaId ?: currentFileInfo?.uriString
        val position = positionMsOverride ?: currentPositionSafeMs()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_RESUME_MEDIA_URI, currentUri)
            .putString(KEY_RESUME_QUEUE_URIS, playlistQueue.joinToString("\n") { it.uriString })
            .putInt(KEY_RESUME_QUEUE_INDEX, currentQueueIndex)
            .putString(KEY_RESUME_QUEUE_TITLE, currentPlaylistName)
            .putLong(KEY_RESUME_POSITION_MS, position)
            .putInt(KEY_RESUME_REPEAT_MODE, repeatMode)
            .apply()
    }

    private fun currentPositionSafeMs(): Long =
        runCatching { mediaPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    private fun unduckIfNeeded() {
        if (!isDuckedForFocusLoss) return
        mediaPlayer?.setVolume(1.0f, 1.0f)
        isDuckedForFocusLoss = false
    }

    private fun resourceIconUri(drawableResId: Int): Uri =
        Uri.parse("android.resource://$packageName/$drawableResId")

    private fun bundleOfContentStyle(key: String, value: Int): Bundle =
        Bundle().apply { putInt(key, value) }
}
