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
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.app.SearchManager
import android.widget.Toast
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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
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
        private const val PLAYLIST_SHORT_PREFIX = "pl:"
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
        private const val KEY_SCAN_WHOLE_DRIVE = "scan_whole_drive"
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
        private const val KEY_DEBUG_CLOUD_ANNOUNCEMENTS = "debug_cloud_announcements"
        private const val SMART_PLAYLIST_FAVORITES = "favorites"
        private const val SMART_PLAYLIST_RECENTLY_ADDED = "recently_added"
        private const val SMART_PLAYLIST_MOST_PLAYED = "most_played"
        private const val SMART_PLAYLIST_NOT_HEARD_RECENTLY = "not_heard_recently"
        private const val KEY_FLAGGED_URIS = "flagged_uris"
        private const val SMART_PLAYLIST_FLAGGED = "flagged"
        private const val CUSTOM_ACTION_FLAG = "FLAG_TRACK"

        private const val ACTION_SET_MEDIA_FILES = "SET_MEDIA_FILES"
        private const val ACTION_REFRESH_LIBRARY = "REFRESH_LIBRARY"
        private const val ACTION_SET_PLAYLISTS = "SET_PLAYLISTS"
        private const val ACTION_PLAY_SEARCH_LIST = "PLAY_SEARCH_LIST"
        private const val ACTION_PLAY_UI_LIST = "PLAY_UI_LIST"
        private const val ACTION_SET_TRACK_VOICE_INTRO = "SET_TRACK_VOICE_INTRO"
        private const val ACTION_SET_TRACK_VOICE_OUTRO = "SET_TRACK_VOICE_OUTRO"
        private const val ACTION_SET_DEBUG_CLOUD = "SET_DEBUG_CLOUD"
        const val ACTION_BT_AUTOPLAY = "BT_AUTOPLAY"

        private var prefsInstance: android.content.SharedPreferences? = null

        @Synchronized
        fun getPrefs(context: Context): android.content.SharedPreferences {
            val existingPrefs = prefsInstance
            if (existingPrefs != null) { return existingPrefs }
            val masterKey = androidx.security.crypto.MasterKey.Builder(context).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            val encryptedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(context, "${PREFS_NAME}_encrypted", masterKey, androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            val standardPrefsFile = File(context.applicationInfo.dataDir, "shared_prefs/${PREFS_NAME}.xml")
            if (standardPrefsFile.exists() && !encryptedPrefs.getBoolean("migration_completed", false)) {
                val standardPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = encryptedPrefs.edit()
                for ((key, value) in standardPrefs.all) {
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                }
                try {
                    @Suppress("ApplySharedPref")
                    editor.putBoolean("migration_completed", true).commit()
                    @Suppress("ApplySharedPref")
                    standardPrefs.edit().clear().commit()
                    standardPrefsFile.delete()
                } catch (e: Exception) {
                    // Log error - migration will retry on next app launch
                }
            }
            prefsInstance = encryptedPrefs
            return encryptedPrefs
        }
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
        private const val EXTRA_DEBUG_CLOUD_ENABLED = "debug_cloud_enabled"

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

        private val SEARCH_PREFIX_REGEX = Regex("^(?:\\s*(?:play|shuffle)\\b\\s*)+")
        private val SHUFFLE_REGEX = Regex("\\bshuffle\\b")
    }

    private data class PendingSpeechAction(
        val utteranceId: String,
        val onComplete: () -> Unit
    )

    private lateinit var session: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    internal val mediaCacheService = MediaCacheService()
    private val playlistService = PlaylistService()
    private val playlistShortIds = mutableMapOf<String, String>() // shortId -> uriString
    private val uriToShortId = mutableMapOf<String, String>() // uriString -> shortId
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
    private var debugCloudAnnouncementsEnabled: Boolean = false
    private var lastIntroTemplateIndex: Int = -1
    private var lastOutroTemplateIndex: Int = -1
    private var announcementPreGenerator: AnnouncementPreGenerator? = null
    private var announcementPlayer: MediaPlayer? = null
    private val announcementJobRef = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>(null)

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
                        val title = titles?.getOrNull(i).orEmpty().ifBlank { names[i].substringBeforeLast('.') }
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
                        val prefs = getPrefs(this@MyMusicService)
                        val treeUriStr = prefs.getString(KEY_TREE_URI, null)
                        if (treeUriStr != null) {
                            val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
                            mediaCacheService.persistCache(this@MyMusicService, Uri.parse(treeUriStr), limit)
                        }
                    }
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(HOME_ID)
                    notifyChildrenChanged(SONGS_ID)
                    notifyChildrenChanged(PLAYLISTS_ID)
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
                    serviceScope.launch {
                        mediaCacheService.persistPlaylists(this@MyMusicService)
                    }
                    rebuildPlaylistShortIds()
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(PLAYLISTS_ID)
                }
                ACTION_SET_TRACK_VOICE_INTRO -> {
                    val enabled = extras?.getBoolean(EXTRA_TRACK_VOICE_INTRO_ENABLED) ?: false
                    trackVoiceIntroEnabled = enabled
                    getPrefs(this@MyMusicService)
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
                    getPrefs(this@MyMusicService)
                        .edit()
                        .putBoolean(KEY_TRACK_VOICE_OUTRO_ENABLED, enabled)
                        .apply()
                    if (enabled) {
                        ensureTextToSpeechInitialized()
                    } else if (!trackVoiceIntroEnabled) {
                        clearPendingIntro()
                    }
                }
                ACTION_SET_DEBUG_CLOUD -> {
                    val enabled = extras?.getBoolean(EXTRA_DEBUG_CLOUD_ENABLED) ?: false
                    debugCloudAnnouncementsEnabled = enabled
                    getPrefs(this@MyMusicService)
                        .edit()
                        .putBoolean(KEY_DEBUG_CLOUD_ANNOUNCEMENTS, enabled)
                        .apply()
                }
                CUSTOM_ACTION_FLAG -> {
                    val uri = currentFileInfo?.uriString ?: currentMediaId ?: return
                    val prefs = getPrefs(this@MyMusicService)
                    val flagged = prefs.getStringSet(KEY_FLAGGED_URIS, emptySet())
                        ?.toMutableSet() ?: mutableSetOf()
                    if (uri in flagged) {
                        flagged.remove(uri)
                    } else {
                        flagged.add(uri)
                    }
                    prefs.edit().putStringSet(KEY_FLAGGED_URIS, flagged).apply()
                    // Refresh playback state to toggle the icon
                    val currentState = lastPlaybackState()?.state
                        ?: PlaybackStateCompat.STATE_NONE
                    updatePlaybackState(currentState)
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

        trackVoiceIntroEnabled = getPrefs(this@MyMusicService)
            .getBoolean(KEY_TRACK_VOICE_INTRO_ENABLED, false)
        trackVoiceOutroEnabled = getPrefs(this@MyMusicService)
            .getBoolean(KEY_TRACK_VOICE_OUTRO_ENABLED, false)
        debugCloudAnnouncementsEnabled = getPrefs(this@MyMusicService)
            .getBoolean(KEY_DEBUG_CLOUD_ANNOUNCEMENTS, false)
        if (trackVoiceIntroEnabled || trackVoiceOutroEnabled) {
            ensureTextToSpeechInitialized()
        }
        announcementPreGenerator = AnnouncementPreGenerator(this, serviceScope)

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
        announcementPreGenerator?.cancelAll()
        announcementPreGenerator = null
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
            val rawQuery = intent.getStringExtra(SearchManager.QUERY)
            val query = if (rawQuery != null && rawQuery.length > 500) {
                rawQuery.substring(0, 500)
            } else {
                rawQuery
            }

            val extras = intent.extras?.let { originalExtras ->
                Bundle().apply {
                    val allowedKeys = setOf(
                        "android.intent.extra.focus",
                        "android.intent.extra.artist",
                        "android.intent.extra.album",
                        "android.intent.extra.genre",
                        "android.intent.extra.title",
                        "android.intent.extra.playlist"
                    )
                    for (key in allowedKeys) {
                        val value = originalExtras.getString(key)
                        if (value != null) {
                            putString(key, if (value.length > 500) value.substring(0, 500) else value)
                        }
                    }
                }
            }
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

    private fun buildMediaItemsForPrefix(parentId: String): MutableList<MediaItem>? {
        return when {
            parentId.startsWith(SMART_PLAYLIST_PREFIX) -> buildMediaItemsForSmartPlaylist(parentId)
            parentId.startsWith(PLAYLIST_PREFIX) -> buildMediaItemsForPlaylist(parentId)
            parentId.startsWith(ALBUM_PREFIX) -> buildMediaItemsForAlbum(parentId)
            parentId.startsWith(GENRE_PREFIX) -> buildMediaItemsForGenre(parentId)
            parentId.startsWith(GENRE_SONG_LETTER_PREFIX) -> buildMediaItemsForGenreSongLetter(parentId)
            parentId.startsWith(ARTIST_PREFIX) -> buildMediaItemsForArtist(parentId)
            parentId.startsWith(ARTIST_LETTER_PREFIX) -> buildMediaItemsForArtistLetter(parentId)
            parentId.startsWith(DECADE_PREFIX) -> buildMediaItemsForDecade(parentId)
            parentId.startsWith(DECADE_SONG_LETTER_PREFIX) -> buildMediaItemsForDecadeSongLetter(parentId)
            parentId.startsWith(GENRE_LETTER_PREFIX) -> buildMediaItemsForGenreLetter(parentId)
            parentId.startsWith(SONG_LETTER_PREFIX) -> buildMediaItemsForSongLetter(parentId)
            else -> null
        }
    }

    private fun buildMediaItemsForSmartPlaylist(parentId: String): MutableList<MediaItem> {
            val smartId = Uri.decode(parentId.removePrefix(SMART_PLAYLIST_PREFIX))
            val tracks = resolveSmartPlaylistTracksById(smartId) ?: emptyList()
            return buildSongListItems(
                tracks,
                SMART_PLAYLIST_PREFIX + Uri.encode(smartId),
                resourceIconUri(R.drawable.ic_album_placeholder)
            )
    }

    private fun buildMediaItemsForPlaylist(parentId: String): MutableList<MediaItem> {
            val shortId = parentId.removePrefix(PLAYLIST_PREFIX)
            val playlistUri = playlistShortIds[shortId] ?: return mutableListOf()
            val songs = enrichFromCache(
                playlistService.readPlaylist(this, Uri.parse(playlistUri))
            )
            val songIconUri = resourceIconUri(R.drawable.ic_album_placeholder)
            return buildSongListItems(songs, PLAYLIST_SHORT_PREFIX + shortId, songIconUri)
    }

    private fun buildMediaItemsForAlbum(parentId: String): MutableList<MediaItem> {
            ensureMetadataIndexes()
            val album = Uri.decode(parentId.removePrefix(ALBUM_PREFIX))
            return buildSongListItems(mediaCacheService.songsForAlbum(album), ALBUM_PREFIX + Uri.encode(album), resourceIconUri(R.drawable.ic_album_placeholder))
    }

    private fun buildMediaItemsForGenre(parentId: String): MutableList<MediaItem> {
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

    private fun buildMediaItemsForGenreSongLetter(parentId: String): MutableList<MediaItem> {
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

    private fun buildMediaItemsForArtist(parentId: String): MutableList<MediaItem> {
            ensureMetadataIndexes()
            val artist = Uri.decode(parentId.removePrefix(ARTIST_PREFIX))
            return buildSongListItems(mediaCacheService.songsForArtist(artist), ARTIST_PREFIX + Uri.encode(artist), resourceIconUri(R.drawable.ic_album_placeholder))
    }

    private fun buildMediaItemsForArtistLetter(parentId: String): MutableList<MediaItem> {
            ensureMetadataIndexes()
            val letter = Uri.decode(parentId.removePrefix(ARTIST_LETTER_PREFIX))
            return buildCategoryListItems(
                filterByLetter(mediaCacheService.artists(), letter),
                ARTIST_PREFIX,
                buildArtistCounts(mediaCacheService.cachedFiles),
                resourceIconUri(R.drawable.ic_auto_artists)
            )
    }

    private fun buildMediaItemsForDecade(parentId: String): MutableList<MediaItem> {
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

    private fun buildMediaItemsForDecadeSongLetter(parentId: String): MutableList<MediaItem> {
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

    private fun buildMediaItemsForGenreLetter(parentId: String): MutableList<MediaItem> {
            ensureMetadataIndexes()
            val letter = Uri.decode(parentId.removePrefix(GENRE_LETTER_PREFIX))
            return buildCategoryListItems(
                filterByLetter(mediaCacheService.genres(), letter),
                GENRE_PREFIX,
                iconUri = resourceIconUri(R.drawable.ic_auto_genres)
            )
    }

    private fun buildMediaItemsForSongLetter(parentId: String): MutableList<MediaItem> {
            val letter = Uri.decode(parentId.removePrefix(SONG_LETTER_PREFIX))
            val filtered = filterSongsByLetter(mediaCacheService.cachedFiles, letter)
            return buildSongLetterItems(
                SONG_LETTER_PREFIX + Uri.encode(letter),
                filtered
            )
    }

    private fun buildMediaItemsForId(parentId: String): MutableList<MediaItem> {
        return when (parentId) {
            ROOT_ID -> buildRootItems()
            HOME_ID -> buildHomeItems()
            SONGS_ID -> buildSongsItems()
            SONGS_ALL_ID -> buildSongsAllItems()
            PLAYLISTS_ID -> buildPlaylistsItems()
            ALBUMS_ID -> buildAlbumsItems()
            GENRES_ID -> buildGenresItems()
            ARTISTS_ID -> buildArtistsItems()
            DECADES_ID -> buildDecadesItems()
            SEARCH_ID -> buildSearchItems()
            else -> mutableListOf()
        }
    }

    private fun buildRootItems(): MutableList<MediaItem> {
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
        return items
    }

    private fun buildSongsItems(): MutableList<MediaItem> {
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
        return items
    }

    private fun buildSongsAllItems(): MutableList<MediaItem> {
        val titles = mediaCacheService.cachedFiles.map { it.cleanTitle }
        return buildCategoryListItems(
            buildLetterBuckets(titles),
            SONG_LETTER_PREFIX,
            iconUri = resourceIconUri(R.drawable.ic_auto_song)
        )
    }

    private fun buildPlaylistsItems(): MutableList<MediaItem> {
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
        items += playlistEntriesForBrowse(mediaCacheService.discoveredPlaylists).map { entry ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(entry.mediaId)
                .setTitle(entry.title)
                .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                .build()
            MediaItem(description, MediaItem.FLAG_BROWSABLE)
        }
        return items
    }

    private fun buildAlbumsItems(): MutableList<MediaItem> {
        ensureMetadataIndexes()
        return buildCategoryListItems(
            mediaCacheService.albums(),
            ALBUM_PREFIX,
            buildAlbumCounts(mediaCacheService.cachedFiles),
            resourceIconUri(R.drawable.ic_auto_albums)
        )
    }

    private fun buildGenresItems(): MutableList<MediaItem> {
        ensureMetadataIndexes()
        return buildCategoryListItems(
            mediaCacheService.genres(),
            GENRE_PREFIX,
            buildGenreCounts(mediaCacheService.cachedFiles),
            resourceIconUri(R.drawable.ic_auto_genres)
        )
    }

    private fun buildArtistsItems(): MutableList<MediaItem> {
        ensureMetadataIndexes()
        return buildCategoryListItems(
            buildLetterBuckets(mediaCacheService.artists()),
            ARTIST_LETTER_PREFIX,
            iconUri = resourceIconUri(R.drawable.ic_auto_artists)
        )
    }

    private fun buildDecadesItems(): MutableList<MediaItem> {
        ensureMetadataIndexes()
        return buildCategoryListItems(
            mediaCacheService.decades(),
            DECADE_PREFIX,
            buildDecadeCounts(mediaCacheService.cachedFiles),
            resourceIconUri(R.drawable.ic_auto_decades)
        )
    }

    private fun buildSearchItems(): MutableList<MediaItem> {
        return mutableListOf(
            MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("search_hint")
                    .setTitle("Use the search icon to search")
                    .build(),
                MediaItem.FLAG_BROWSABLE
            )
        )
    }
    internal fun buildMediaItems(parentId: String): MutableList<MediaItem> {
        val start = SystemClock.elapsedRealtime()

        val prefixItems = buildMediaItemsForPrefix(parentId)
        if (prefixItems != null) {
            val elapsed = SystemClock.elapsedRealtime() - start
            Log.d("MyMusicService", "buildMediaItems($parentId) -> ${prefixItems.size} in ${elapsed}ms")
            return prefixItems
        }

        val items = buildMediaItemsForId(parentId)

        val elapsed = SystemClock.elapsedRealtime() - start
        Log.d("MyMusicService", "buildMediaItems($parentId) -> ${items.size} in ${elapsed}ms")
        return items
    }

    private fun buildLetterBuckets(values: List<String>): List<String> {
        val seenLetters = BooleanArray(26)
        var hasOther = false

        for (value in values) {
            var foundNonWhitespace = false
            for (i in 0 until value.length) {
                val c = value[i]
                if (!c.isWhitespace()) {
                    foundNonWhitespace = true
                    val u = c.uppercaseChar()
                    if (u in 'A'..'Z') {
                        seenLetters[u - 'A'] = true
                    } else {
                        hasOther = true
                    }
                    break
                }
            }
            if (!foundNonWhitespace) {
                hasOther = true
            }
        }

        val result = ArrayList<String>(27)
        for (i in 0..25) {
            if (seenLetters[i]) {
                result.add((i + 'A'.code).toChar().toString())
            }
        }
        if (hasOther) {
            result.add("#")
        }
        return result
    }

    internal data class PlaylistBrowseEntry(
        val mediaId: String,
        val title: String
    )

    private fun rebuildPlaylistShortIds() {
        playlistShortIds.clear()
        uriToShortId.clear()
        for (playlist in mediaCacheService.discoveredPlaylists) {
            var shortId = playlistShortId(playlist.uriString)
            var attempt = 0
            while (playlistShortIds.containsKey(shortId) &&
                   playlistShortIds[shortId] != playlist.uriString) {
                attempt++
                shortId = playlistShortId(playlist.uriString + attempt)
            }
            playlistShortIds[shortId] = playlist.uriString
            uriToShortId[playlist.uriString] = shortId
        }
    }

    @VisibleForTesting
    internal fun playlistEntriesForBrowse(discoveredPlaylists: List<PlaylistInfo>): List<PlaylistBrowseEntry> {
        val discoveredEntries = discoveredPlaylists.map { playlist ->
            val shortId = uriToShortId[playlist.uriString]
                ?: playlistShortId(playlist.uriString)
            PlaylistBrowseEntry(
                mediaId = PLAYLIST_PREFIX + shortId,
                title = playlist.displayName.removeSuffix(".m3u")
            )
        }
        val smartEntries = listOf(
            SMART_PLAYLIST_FAVORITES to "Favorites",
            SMART_PLAYLIST_FLAGGED to "Flagged",
            SMART_PLAYLIST_RECENTLY_ADDED to "Recently Added",
            SMART_PLAYLIST_MOST_PLAYED to "Most Played",
            SMART_PLAYLIST_NOT_HEARD_RECENTLY to "Haven't Heard In A While"
        ).map { smart ->
            PlaylistBrowseEntry(
                mediaId = SMART_PLAYLIST_PREFIX + Uri.encode(smart.first),
                title = smart.second
            )
        }
        return discoveredEntries + smartEntries
    }

    private fun filterByLetter(values: List<String>, letter: String): List<String> {
        if (letter == "#") {
            return values.filter {
                val first = it.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
                first == null || first !in 'A'..'Z'
            }.sorted()
        }
        val target = letter.firstOrNull()?.uppercaseChar() ?: return emptyList()
        return values.filter {
            it.firstOrNull { !it.isWhitespace() }?.uppercaseChar() == target
        }.sorted()
    }

    private fun filterSongsByLetter(
        songs: List<MediaFileInfo>,
        letter: String
    ): List<MediaFileInfo> {
        val target = letter.firstOrNull()?.uppercaseChar()
        return songs.filter { file ->
            val first = file.cleanTitle.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
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
        val presentLetters = BooleanArray(26)
        var hasOther = false
        for (song in songs) {
            val title = song.cleanTitle
            var firstChar: Char? = null
            for (i in title.indices) {
                val c = title[i]
                if (!c.isWhitespace()) {
                    firstChar = c.uppercaseChar()
                    break
                }
            }
            if (firstChar != null && firstChar in 'A'..'Z') {
                presentLetters[firstChar - 'A'] = true
            } else {
                hasOther = true
            }
        }
        val result = mutableListOf<String>()
        for (i in presentLetters.indices) {
            if (presentLetters[i]) {
                result.add((i + 'A'.code).toChar().toString())
            }
        }
        if (hasOther) result.add("#")
        return result
    }

    @VisibleForTesting
    internal fun buildSongLetterCounts(songs: List<MediaFileInfo>): Map<String, Int> {
        return songs.groupingBy { song ->
            val first = song.cleanTitle.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
            if (first != null && first in 'A'..'Z') first.toString() else "#"
        }.eachCount()
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
        val prefs = getPrefs(this@MyMusicService)
        val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
        val wholeDriveMode = prefs.getBoolean(KEY_SCAN_WHOLE_DRIVE, false)
        val uri = if (wholeDriveMode) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
            val parsed = Uri.parse(uriString)
            val hasPermission = contentResolver.persistedUriPermissions.any {
                it.uri == parsed && it.isReadPermission
            }
            if (!hasPermission) return
            parsed
        }

        // Set isScanning BEFORE launching the coroutine so that any onLoadChildren
        // calls during the loading window are queued in pendingResults and delivered
        // properly once data is ready — rather than returning empty results immediately.
        isScanning = true
        serviceScope.launch {
            try {
                val persisted = mediaCacheService.loadPersistedCache(this@MyMusicService, uri, limit)
                if (persisted != null && persisted.files.isNotEmpty()) {
                    mediaCacheService.buildAlbumArtistIndexesFromCache()
                } else {
                    var lastNotify = 0
                    val progress: (Int, Int) -> Unit = { songsFound, _ ->
                        if (songsFound - lastNotify >= 200) {
                            lastNotify = songsFound
                            notifyChildrenChanged(SONGS_ALL_ID)
                        }
                    }
                    if (wholeDriveMode) {
                        mediaCacheService.scanWholeDevice(this@MyMusicService, limit, progress)
                    } else {
                        mediaCacheService.scanDirectory(
                            ScanContext(
                                context = this@MyMusicService,
                                treeUri = uri,
                                maxFiles = limit,
                                onProgress = progress
                            )
                        )
                        mediaCacheService.enrichGenresFromMediaStore(this@MyMusicService)
                    }
                    mediaCacheService.buildAlbumArtistIndexesFromCache()
                    mediaCacheService.persistCache(this@MyMusicService, uri, limit)
                }
            } finally {
                isScanning = false
                refreshQueueMetadata()
                rebuildPlaylistShortIds()
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
        val byUri = mediaCacheService.getFileIndexByUri()
        return files.map { file -> byUri[file.uriString] ?: file }
    }

    private fun enrichWithMetadata(fileInfo: MediaFileInfo): MediaFileInfo {
        val runtimeMetadata = MediaMetadataHelper.extractMetadata(this, fileInfo.uriString)
        return if (runtimeMetadata != null) {
            fileInfo.copy(
                title = runtimeMetadata.title?.takeIf { it.isNotBlank() } ?: fileInfo.title,
                artist = runtimeMetadata.artist?.takeIf { it.isNotBlank() } ?: fileInfo.artist,
                album = runtimeMetadata.album?.takeIf { it.isNotBlank() } ?: fileInfo.album
            )
        } else fileInfo
    }

    private fun schedulePreGenerationIfNeeded(enrichedFileInfo: MediaFileInfo) {
        if (trackVoiceIntroEnabled || trackVoiceOutroEnabled) {
            val nextTrack = peekNextQueueTrack()
            val enrichedNext = nextTrack?.let { enrichWithMetadata(it) }
            announcementPreGenerator?.schedulePreGeneration(enrichedFileInfo, enrichedNext)
        }
    }

    private fun prepareAndStartMediaPlayer(fileInfo: MediaFileInfo, enrichedFileInfo: MediaFileInfo) {
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
                    updateMetadata(enrichedFileInfo)
                    maybeSpeakTrackIntroThenStart(enrichedFileInfo, this)
                }
                setOnCompletionListener {
                    Log.d("MyMusicService", "Track completed: ${enrichedFileInfo.cleanTitle}, calling maybeSpeakTrackFinished")
                    maybeSpeakTrackFinishedThenAdvance(enrichedFileInfo)
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
        } catch (e: Exception) {
            handleMediaPlayerSetupError(e, player, fileInfo)
        }
    }

    private fun handleMediaPlayerSetupError(e: Exception, player: MediaPlayer, fileInfo: MediaFileInfo) {
        val (errorMsg, logMsg) = when (e) {
            is SecurityException -> "Permission denied: ${fileInfo.displayName}" to "Permission denied for ${fileInfo.displayName}"
            is java.io.IOException -> "Cannot read: ${fileInfo.displayName}" to "IO error for ${fileInfo.displayName}"
            is IllegalArgumentException -> "Invalid file: ${fileInfo.displayName}" to "Invalid URI for ${fileInfo.displayName}"
            else -> "Cannot play: ${fileInfo.displayName}" to "Unexpected error for ${fileInfo.displayName}"
        }
        Log.e("MyMusicService", logMsg, e)
        try { player.release() } catch (_: Exception) {}
        mediaPlayer = null
        abandonAudioFocus()
        handlePlaybackError(errorMsg)
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

        val enrichedFileInfo = enrichWithMetadata(fileInfo)
        schedulePreGenerationIfNeeded(enrichedFileInfo)
        prepareAndStartMediaPlayer(fileInfo, enrichedFileInfo)
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
        val title = fileInfo.cleanTitle
        val artist = fileInfo.artist?.takeIf { it.isNotBlank() }
        val job = serviceScope.launch {
            val audioFile = announcementPreGenerator?.getReadyAudio(fileInfo, isIntro = true)
            mainHandler.post {
                if (audioFile != null) {
                    if (debugCloudAnnouncementsEnabled) {
                        Toast.makeText(this@MyMusicService, "Using cloud intro (Kilo + Google TTS)", Toast.LENGTH_SHORT).show()
                    }
                    playAnnouncementFile(audioFile, onComplete)
                } else {
                    if (debugCloudAnnouncementsEnabled) {
                        val prefs = getPrefs(this@MyMusicService)
                        val hasTtsKey = ApiKeyStore.getPrefs(this@MyMusicService)?.let { p ->
                            p.getString(ApiKeyStore.KEY_CLOUD_TTS, null)?.isNotBlank() == true
                        } ?: false
                        val msg = if (hasTtsKey) {
                            "Android TTS (cloud gen timed out - increase timeout)"
                        } else {
                            "Android TTS (no TTS key - using on-device)"
                        }
                        Toast.makeText(this@MyMusicService, msg, Toast.LENGTH_SHORT).show()
                    }
                    speakWithTts(buildIntroAnnouncement(artist, title), onComplete)
                }
            }
        }
        announcementJobRef.set(job)
    }

    private fun maybeSpeakTrackFinishedThenAdvance(fileInfo: MediaFileInfo) {
        val onComplete: () -> Unit = {
            mainHandler.post {
                onTrackCompleted()
            }
        }
        if (!trackVoiceOutroEnabled) {
            Log.d("MyMusicService", "Track outro disabled, skipping")
            onComplete()
            return
        }
        Log.d("MyMusicService", "Track outro enabled, getting pre-generated audio for: ${fileInfo.cleanTitle}")
        val title = fileInfo.cleanTitle
        val artist = fileInfo.artist?.takeIf { it.isNotBlank() }
        val job = serviceScope.launch {
            val audioFile = announcementPreGenerator?.getReadyAudio(fileInfo, isIntro = false)
            mainHandler.post {
                if (audioFile != null) {
                    if (debugCloudAnnouncementsEnabled) {
                            Toast.makeText(this@MyMusicService, "Using cloud outro (Kilo + Google TTS)", Toast.LENGTH_SHORT).show()
                    }
                    playAnnouncementFile(audioFile, onComplete)
                } else {
                    if (debugCloudAnnouncementsEnabled) {
                        val hasTtsKey = ApiKeyStore.getPrefs(this@MyMusicService)?.let { p ->
                            p.getString(ApiKeyStore.KEY_CLOUD_TTS, null)?.isNotBlank() == true
                        } ?: false
                        val msg = if (hasTtsKey) {
                            "Android TTS (cloud gen timed out - increase timeout)"
                        } else {
                            "Android TTS (no TTS key - using on-device)"
                        }
                        Toast.makeText(this@MyMusicService, msg, Toast.LENGTH_SHORT).show()
                    }
                    speakWithTts(buildOutroAnnouncement(artist, title), onComplete)
                }
            }
        }
        announcementJobRef.set(job)
    }

    /** Plays a pre-generated audio file for an announcement, then calls [onComplete]. */
    private fun playAnnouncementFile(file: File, onComplete: () -> Unit) {
        releaseAnnouncementPlayer()
        val player = MediaPlayer()
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                releaseAnnouncementPlayer()
                onComplete()
            }
            player.setOnErrorListener { _, _, _ ->
                releaseAnnouncementPlayer()
                onComplete()
                true
            }
            player.prepare()
            player.start()
            announcementPlayer = player
        }.onFailure {
            runCatching { player.release() }
            onComplete()
        }
    }

    private fun releaseAnnouncementPlayer() {
        runCatching { announcementPlayer?.release() }
        announcementPlayer = null
    }

    /** Speaks [ssml] via Android TTS, calling [onComplete] when done or on error. */
    private fun speakWithTts(ssml: String, onComplete: () -> Unit) {
        ensureTextToSpeechInitialized()
        val tts = textToSpeech
        if (!ttsReady || tts == null) {
            onComplete()
            return
        }
        val utteranceId = "announcement_${SystemClock.elapsedRealtime()}"
        val action = PendingSpeechAction(utteranceId, onComplete)
        pendingSpeechAction.set(action)
        val result = tts.speak(ssml, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            if (pendingSpeechAction.compareAndSet(action, null)) {
                onComplete()
            }
        }
    }

    private fun buildIntroAnnouncement(artist: String?, title: String): String {
        val t = title.toSsmlSafe()
        val templates = if (artist != null) {
            val a = artist.toSsmlSafe()
            listOf(
                """<speak>Now playing <emphasis level="moderate">$t</emphasis><break time="150ms"/> by $a.</speak>""",
                """<speak>Up next,<break time="100ms"/> <emphasis level="moderate">$t</emphasis> from $a.</speak>""",
                """<speak>Here comes <emphasis level="moderate">$t</emphasis><break time="100ms"/> by $a.</speak>""",
                """<speak>Let's hear <emphasis level="moderate">$t</emphasis><break time="100ms"/> by $a.</speak>""",
                """<speak>Coming up,<break time="100ms"/> <emphasis level="moderate">$t</emphasis> from $a.</speak>""",
                """<speak>This is <emphasis level="moderate">$t</emphasis><break time="100ms"/> by $a.</speak>"""
            )
        } else {
            listOf(
                """<speak>Now playing <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>Up next,<break time="100ms"/> <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>Here comes <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>Let's hear <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>Coming up,<break time="100ms"/> <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>This is <emphasis level="moderate">$t</emphasis>.</speak>"""
            )
        }
        val (pickedIndex, pickedTemplate) = pickTemplate(templates, lastIntroTemplateIndex)
        lastIntroTemplateIndex = pickedIndex
        return pickedTemplate
    }

    private fun buildOutroAnnouncement(artist: String?, title: String): String {
        val t = title.toSsmlSafe()
        val templates = if (artist != null) {
            val a = artist.toSsmlSafe()
            listOf(
                """<speak>That was <emphasis level="moderate">$t</emphasis><break time="100ms"/> by $a.</speak>""",
                """<speak>You just heard <emphasis level="moderate">$t</emphasis><break time="100ms"/> from $a.</speak>""",
                """<speak>That was <emphasis level="moderate">$t</emphasis> by $a<break time="100ms"/> just now.</speak>""",
                """<speak>We just finished <emphasis level="moderate">$t</emphasis><break time="100ms"/> by $a.</speak>""",
                """<speak>That wraps up <emphasis level="moderate">$t</emphasis><break time="100ms"/> from $a.</speak>""",
                """<speak>Recently played:<break time="150ms"/> <emphasis level="moderate">$t</emphasis> by $a.</speak>"""
            )
        } else {
            listOf(
                """<speak>That was <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>You just heard <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>That was <emphasis level="moderate">$t</emphasis><break time="100ms"/> just now.</speak>""",
                """<speak>We just finished <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>That wraps up <emphasis level="moderate">$t</emphasis>.</speak>""",
                """<speak>Recently played:<break time="150ms"/> <emphasis level="moderate">$t</emphasis>.</speak>"""
            )
        }
        val (pickedIndex, pickedTemplate) = pickTemplate(templates, lastOutroTemplateIndex)
        lastOutroTemplateIndex = pickedIndex
        return pickedTemplate
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

    private fun String.toSsmlSafe(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun clearPendingIntro() {
        announcementJobRef.getAndSet(null)?.cancel()
        releaseAnnouncementPlayer()
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
        Log.w("MyMusicService", "Unable to recover playback error: $message")
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR, errorMessage = message)
        handleStop()
    }

    private fun peekNextQueueTrack(): MediaFileInfo? {
        val nextIndex = currentQueueIndex + 1
        return when {
            nextIndex < playlistQueue.size -> playlistQueue[nextIndex]
            repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL && playlistQueue.isNotEmpty() -> playlistQueue[0]
            else -> null
        }
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

    private suspend fun refreshQueueMetadata() {
        if (playlistQueue.isEmpty()) return
        val byUri = mediaCacheService.getFileIndexByUri()
        playMutex.withLock {
            var changed = false
            playlistQueue = playlistQueue.map { fileInfo ->
                val cached = byUri[fileInfo.uriString]
                if (cached != null && cached.title != fileInfo.title) {
                    changed = true
                    cached
                } else {
                    fileInfo
                }
            }
            if (changed) {
                currentFileInfo = playlistQueue.getOrNull(currentQueueIndex) ?: currentFileInfo
                updateSessionQueue()
                currentFileInfo?.let { updateMetadata(it) }
            }
        }
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

    private suspend fun handlePlayFromMediaId(resolvedMediaId: String) {
        when {
            resolvedMediaId.startsWith(ACTION_PLAY_ALL_PREFIX) ||
            resolvedMediaId.startsWith(ACTION_SHUFFLE_PREFIX) -> {
                handlePlayAllOrShuffle(resolvedMediaId)
            }
            resolvedMediaId.startsWith(PLAYLIST_PREFIX) -> {
                handlePlayPlaylist(resolvedMediaId)
            }
            resolvedMediaId.startsWith(SMART_PLAYLIST_PREFIX) -> {
                handlePlaySmartPlaylist(resolvedMediaId)
            }
            else -> {
                handlePlaySingleItem(resolvedMediaId)
            }
        }
    }

    private suspend fun handlePlayAllOrShuffle(resolvedMediaId: String) {
        val isShuffle = resolvedMediaId.startsWith(ACTION_SHUFFLE_PREFIX)
        val listKey = resolvedMediaId.removePrefix(
            if (isShuffle) ACTION_SHUFFLE_PREFIX else ACTION_PLAY_ALL_PREFIX
        )
        val tracks = when {
            listKey == SONGS_ID -> mediaCacheService.cachedFiles
            listKey == PLAYLISTS_ID -> {
                val all = kotlinx.coroutines.coroutineScope {
                    mediaCacheService.discoveredPlaylists.map { playlist ->
                        async(Dispatchers.IO) {
                            playlistService.readPlaylist(
                                this@MyMusicService,
                                Uri.parse(playlist.uriString)
                            )
                        }
                    }.awaitAll().flatten()
                }
                enrichFromCache(all)
            }
            listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
                val shortId = listKey.removePrefix(PLAYLIST_SHORT_PREFIX)
                val playlistUri = playlistShortIds[shortId] ?: ""
                if (playlistUri.isEmpty()) emptyList()
                else enrichFromCache(
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
            listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
                val shortId = listKey.removePrefix(PLAYLIST_SHORT_PREFIX)
                val uri = playlistShortIds[shortId]
                mediaCacheService.discoveredPlaylists
                    .firstOrNull { it.uriString == uri }
                    ?.displayName ?: "Playlist"
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
    }

    private suspend fun handlePlayPlaylist(resolvedMediaId: String) {
        val shortId = resolvedMediaId.removePrefix(PLAYLIST_PREFIX)
        val playlistUri = playlistShortIds[shortId] ?: return
        val playlistInfo = mediaCacheService.discoveredPlaylists.firstOrNull {
            it.uriString == playlistUri
        }
        val playlistUriToRead = playlistInfo?.uriString ?: playlistUri
        val playlistTracks = enrichFromCache(
            playlistService.readPlaylist(
                this,
                Uri.parse(playlistUriToRead)
            )
        )
        if (playlistTracks.isEmpty()) {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }
        playlistQueue = playlistTracks
        currentQueueIndex = 0
        currentPlaylistName = playlistInfo?.displayName?.removeSuffix(".m3u")
            ?: Uri.parse(playlistUriToRead).lastPathSegment?.removeSuffix(".m3u")
            ?: "Playlist"
        updateSessionQueue()
        playTrack(playlistQueue[currentQueueIndex])
        savePlaybackSnapshot()
    }

    private suspend fun handlePlaySmartPlaylist(resolvedMediaId: String) {
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
    }

    private suspend fun handlePlaySingleItem(resolvedMediaId: String) {
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
        val wantsShuffle = SHUFFLE_REGEX.containsMatchIn(lowered)
        val cleanedQuery = lowered.trim().replaceFirst(SEARCH_PREFIX_REGEX, "").trim()

        val mediaFocus = extras?.getString(EXTRA_MEDIA_FOCUS_KEY)?.trim().orEmpty()
        val requestedPlaylist = extras?.getString(EXTRA_MEDIA_PLAYLIST_KEY)?.trim().orEmpty()
        val focusPlaylist = mediaFocus.equals(FOCUS_PLAYLIST, ignoreCase = true)

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
        if (smartPlaylistId != null && tryPlaySmartPlaylist(smartPlaylistId, wantsShuffle)) {
            return
        }

        if (tryPlayRegularPlaylist(playlistNameQuery, wantsShuffle)) {
            return
        }

        val (focusedMatches, focusLabel) = findFocusedMatches(raw, cleanedQuery, extras)

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

        val finalPlaylistName = when {
            focusLabel != null -> focusLabel
            queryForSongs.isBlank() -> "All Songs"
            else -> "Search: $queryForSongs"
        }

        playQueueAndSaveSnapshot(matches, wantsShuffle, finalPlaylistName)
    }

    private fun playQueueAndSaveSnapshot(
        tracks: List<MediaFileInfo>,
        wantsShuffle: Boolean,
        playlistName: String
    ) {
        playlistQueue = if (wantsShuffle) tracks.shuffled() else tracks
        currentQueueIndex = 0
        currentPlaylistName = playlistName
        updateSessionQueue()
        playTrack(playlistQueue[currentQueueIndex])
        savePlaybackSnapshot()
    }

    private fun tryPlaySmartPlaylist(smartPlaylistId: String, wantsShuffle: Boolean): Boolean {
        val smartTracks = resolveSmartPlaylistTracksById(smartPlaylistId)
        if (smartTracks != null && smartTracks.isNotEmpty()) {
            playQueueAndSaveSnapshot(
                smartTracks,
                wantsShuffle,
                smartPlaylistTitleFromId(smartPlaylistId)
            )
            return true
        }
        return false
    }

    private suspend fun tryPlayRegularPlaylist(
        playlistNameQuery: String,
        wantsShuffle: Boolean
    ): Boolean {
        if (playlistNameQuery.isBlank()) return false
        val playlist = mediaCacheService.discoveredPlaylists.firstOrNull {
            it.displayName.removeSuffix(".m3u").contains(playlistNameQuery, ignoreCase = true)
        }
        if (playlist != null) {
            val tracks = enrichFromCache(
                playlistService.readPlaylist(this, Uri.parse(playlist.uriString))
            )
            if (tracks.isNotEmpty()) {
                playQueueAndSaveSnapshot(
                    tracks,
                    wantsShuffle,
                    playlist.displayName.removeSuffix(".m3u")
                )
                return true
            }
        }
        return false
    }

    private fun findFocusedMatches(
        raw: String,
        cleanedQuery: String,
        extras: Bundle?
    ): Pair<List<MediaFileInfo>?, String?> {
        val requestedArtist = extras?.getString(EXTRA_MEDIA_ARTIST_KEY)?.trim().orEmpty()
        val requestedAlbum = extras?.getString(EXTRA_MEDIA_ALBUM_KEY)?.trim().orEmpty()
        val requestedGenre = extras?.getString(EXTRA_MEDIA_GENRE_KEY)?.trim().orEmpty()
        val requestedTitle = extras?.getString(EXTRA_MEDIA_TITLE_KEY)?.trim().orEmpty()

        val mediaFocus = extras?.getString(EXTRA_MEDIA_FOCUS_KEY)?.trim().orEmpty()
        val focusArtist = mediaFocus.equals(FOCUS_ARTIST, ignoreCase = true)
        val focusAlbum = mediaFocus.equals(FOCUS_ALBUM, ignoreCase = true)
        val focusGenre = mediaFocus.equals(FOCUS_GENRE, ignoreCase = true)
        val focusTitle = mediaFocus.equals(FOCUS_TITLE, ignoreCase = true)

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
        return Pair(focusedMatches, focusLabel)
    }

    private suspend fun ensureCacheReadyForSearch() {
        if (mediaCacheService.cachedFiles.isNotEmpty()) return
        val prefs = getPrefs(this@MyMusicService)
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
        val uri = Uri.parse(uriString)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPermission) return
        mediaCacheService.loadPersistedCache(this, uri, limit)
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
            needle.contains("flag") -> SMART_PLAYLIST_FLAGGED
            else -> null
        }
    }

    private fun smartPlaylistTitleFromId(smartId: String): String {
        return when (smartId) {
            SMART_PLAYLIST_FAVORITES -> "Favorites"
            SMART_PLAYLIST_RECENTLY_ADDED -> "Recently Added"
            SMART_PLAYLIST_MOST_PLAYED -> "Most Played"
            SMART_PLAYLIST_NOT_HEARD_RECENTLY -> "Haven't Heard In A While"
            SMART_PLAYLIST_FLAGGED -> "Flagged"
            else -> smartId
        }
    }

    private fun resolveSmartPlaylistTracksById(smartId: String): List<MediaFileInfo>? {
        val all = mediaCacheService.cachedFiles.ifEmpty { playlistQueue }
        if (all.isEmpty()) return null
        return when (smartId) {
            SMART_PLAYLIST_FAVORITES -> {
                val favorites = getPrefs(this@MyMusicService)
                    .getStringSet(KEY_FAVORITE_URIS, emptySet())
                    ?.toSet()
                    ?: emptySet()
                all.filter { it.uriString in favorites }
            }
            SMART_PLAYLIST_FLAGGED -> {
                val flagged = getPrefs(this@MyMusicService)
                    .getStringSet(KEY_FLAGGED_URIS, emptySet())
                    ?.toSet()
                    ?: emptySet()
                all.filter { it.uriString in flagged }
            }
            SMART_PLAYLIST_RECENTLY_ADDED -> {
                all.sortedByDescending { it.addedAtMs ?: Long.MIN_VALUE }
            }
            SMART_PLAYLIST_MOST_PLAYED -> {
                val playCounts = parsePlayCounts(
                    getPrefs(this@MyMusicService).getString(KEY_PLAY_COUNTS, null)
                )
                all.asSequence().mapNotNull { file ->
                    val plays = playCounts[file.uriString] ?: 0
                    if (plays > 0) file to plays else null
                }.sortedByDescending { it.second }.map { it.first }.toList()
            }
            SMART_PLAYLIST_NOT_HEARD_RECENTLY -> {
                val lastPlayedAt = parseLongMap(
                    getPrefs(this@MyMusicService).getString(KEY_LAST_PLAYED_AT, null)
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

        val builder = PlaybackStateCompat.Builder()
            .setActions(playbackActions)
            .setState(state, position, speed, SystemClock.elapsedRealtime())

        if (state == PlaybackStateCompat.STATE_ERROR && errorMessage != null) {
            builder.setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                errorMessage
            )
        } else {
            builder.setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
                null
            )
        }

        if (playlistQueue.isNotEmpty() && currentQueueIndex >= 0) {
            builder.setActiveQueueItemId(currentQueueIndex.toLong())
        } else {
            builder.setActiveQueueItemId(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN)
        }

        // Add flag/unflag custom action for Android Auto
        val currentUri = currentFileInfo?.uriString ?: currentMediaId
        if (currentUri != null) {
            val isFlagged = getPrefs(this@MyMusicService)
                .getStringSet(KEY_FLAGGED_URIS, emptySet())
                ?.contains(currentUri) == true
            val flagIcon = if (isFlagged) R.drawable.ic_flag_filled else R.drawable.ic_flag
            val flagLabel = if (isFlagged) "Unflag" else "Flag"
            builder.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_FLAG, flagLabel, flagIcon
                ).build()
            )
        }

        session.setPlaybackState(builder.build())
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

        val genre = runtimeMetadata?.genre ?: fileInfo.genre
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, fileInfo.uriString)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
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

    @Volatile
    private var cachedPlaceholderArt: Bitmap? = null

    private fun loadPlaceholderArt(): Bitmap? {
        cachedPlaceholderArt?.let { return it }
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_album_placeholder) ?: return null
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        cachedPlaceholderArt = bitmap
        return bitmap
    }

    private fun ensureNotificationChannel() {
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

    internal fun buildHomeItems(): MutableList<MediaItem> {
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
        val songCount = mediaCacheService.cachedFiles.size
        if (playlistCount > 0 || songCount > 0) {
            val playlistSubtitle = if (playlistCount > 0)
                "$playlistCount playlist${if (playlistCount != 1) "s" else ""}"
            else
                "Smart Playlists"
            items.add(
                createBrowsableMediaItem(
                    mediaId = PLAYLISTS_ID,
                    title = "Playlists",
                    subtitle = playlistSubtitle,
                    iconResId = R.drawable.ic_auto_playlists,
                    extras = childListExtras
                )
            )
        }

        if (songCount > 0) {
            ensureMetadataIndexes()
            val genreCount = mediaCacheService.genres().size
            val decadeCount = mediaCacheService.decades().size
            val albumCount = mediaCacheService.albums().size
            val artistCount = mediaCacheService.artists().size

            items.add(
                createBrowsableMediaItem(
                    mediaId = GENRES_ID,
                    title = "Genres",
                    subtitle = "$genreCount genre${if (genreCount != 1) "s" else ""}",
                    iconResId = R.drawable.ic_auto_genres,
                    extras = childListExtras
                )
            )
            items.add(
                createBrowsableMediaItem(
                    mediaId = DECADES_ID,
                    title = "Decades",
                    subtitle = "$decadeCount decade${if (decadeCount != 1) "s" else ""}",
                    iconResId = R.drawable.ic_auto_decades,
                    extras = childListExtras
                )
            )
            items.add(
                createBrowsableMediaItem(
                    mediaId = ALBUMS_ID,
                    title = "Albums",
                    subtitle = "$albumCount album${if (albumCount != 1) "s" else ""}",
                    iconResId = R.drawable.ic_auto_albums,
                    extras = childListExtras
                )
            )
            items.add(
                createBrowsableMediaItem(
                    mediaId = ARTISTS_ID,
                    title = "Artists",
                    subtitle = "$artistCount artist${if (artistCount != 1) "s" else ""}",
                    iconResId = R.drawable.ic_auto_artists,
                    extras = childListExtras
                )
            )
            items.add(
                createBrowsableMediaItem(
                    mediaId = SONGS_ID,
                    title = "Songs",
                    subtitle = "$songCount song${if (songCount != 1) "s" else ""}",
                    iconResId = R.drawable.ic_auto_song,
                    extras = childListExtras
                )
            )
        }
        return items
    }

    private fun createBrowsableMediaItem(
        mediaId: String,
        title: String,
        subtitle: String,
        iconResId: Int,
        extras: Bundle
    ): MediaItem {
        return MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIconUri(resourceIconUri(iconResId))
                .setExtras(extras)
                .build(),
            MediaItem.FLAG_BROWSABLE
        )
    }

    private fun restorePlaybackSnapshot() {
        val prefs = getPrefs(this@MyMusicService)
        repeatMode = prefs.getInt(KEY_RESUME_REPEAT_MODE, PlaybackStateCompat.REPEAT_MODE_NONE)
        session.setRepeatMode(repeatMode)

        val queueUrisRaw = prefs.getString(KEY_RESUME_QUEUE_URIS, null).orEmpty()
        val queueUris = if (queueUrisRaw.isBlank()) {
            emptyList()
        } else {
            queueUrisRaw.split('\n').filter { it.isNotBlank() }
        }
        if (queueUris.isNotEmpty()) {
            val byUri = mediaCacheService.getFileIndexByUri()
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
                ?: mediaCacheService.getFileByUri(savedMediaUri)
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
        getPrefs(this@MyMusicService)
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
