package com.example.mymediaplayer.shared

import android.content.ComponentCallbacks2
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
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.LruCache
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
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media.utils.MediaConstants
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.exoplayer.MetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.guava.await
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MyMusicService : MediaBrowserServiceCompat() {

    companion object {
        // Top-level browse IDs and category prefixes are `internal` so test code can
        // reference them; leaf sub-prefixes (letters, smart-playlist categories,
        // short-id forms) stay `private` as implementation details.
        internal const val ROOT_ID = "root"
        internal const val HOME_ID = "home"
        internal const val SONGS_ID = "songs"
        internal const val SONGS_ALL_ID = "songs_all"
        internal const val PLAYLISTS_ID = "playlists"
        internal const val ALBUMS_ID = "albums"
        internal const val GENRES_ID = "genres"
        internal const val ARTISTS_ID = "artists"
        internal const val DECADES_ID = "decades"
        internal const val SEARCH_ID = "search"
        private const val SEARCH_QUERY_PREFIX = "search_query:"
        internal const val PLAYLIST_PREFIX = "playlist:"
        internal const val SMART_PLAYLIST_PREFIX = "smart_playlist:"
        private const val PLAYLIST_SHORT_PREFIX = "pl:"
        internal const val ALBUM_PREFIX = "album:"
        internal const val GENRE_PREFIX = "genre:"
        private const val GENRE_SONG_LETTER_PREFIX = "genre_song_letter:"
        internal const val ARTIST_PREFIX = "artist:"
        private const val DECADE_PREFIX = "decade:"
        private const val DECADE_SONG_LETTER_PREFIX = "decade_song_letter:"
        private const val ARTIST_LETTER_PREFIX = "artist_letter:"
        private const val GENRE_LETTER_PREFIX = "genre_letter:"
        private const val SONG_LETTER_PREFIX = "song_letter:"
        private const val SONG_BUCKET_THRESHOLD = 500
        private val SEARCH_INTENT_SANITIZE_REGEX = Regex("[<>]")
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
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val MAX_SEARCH_HISTORY = 10
        private const val SMART_PLAYLIST_FAVORITES = "favorites"
        private const val SMART_PLAYLIST_RECENTLY_ADDED = "recently_added"
        private const val SMART_PLAYLIST_MOST_PLAYED = "most_played"
        private const val SMART_PLAYLIST_NOT_HEARD_RECENTLY = "not_heard_recently"
        private const val KEY_FLAGGED_URIS = "flagged_uris"
        private const val SMART_PLAYLIST_FLAGGED = "flagged"
        private const val CUSTOM_ACTION_FLAG = "FLAG_TRACK"

        private val FOLDER_ART_CANDIDATES = listOf(
            "cover.jpg", "folder.jpg", "front.jpg",
            "Cover.jpg", "Folder.jpg", "Front.jpg",
            "cover.png", "folder.png", "front.png"
        )

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

        @VisibleForTesting
        @Synchronized
        internal fun clearPrefsCacheForTesting() {
            prefsInstance = null
        }

        @Synchronized
        fun getPrefs(context: Context): android.content.SharedPreferences {
            val existingPrefs = prefsInstance
            if (existingPrefs != null) { return existingPrefs }
            val encryptedPrefs = EncryptedPrefsManager.createOrGet(context, "${PREFS_NAME}_encrypted")
            val standardPrefsFile = File(context.applicationInfo.dataDir, "shared_prefs/${PREFS_NAME}.xml")
            val standardPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            if (encryptedPrefs != null) {
                if (standardPrefsFile.exists() && !encryptedPrefs.getBoolean("migration_completed", false)) {
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
                        Log.e("MyMusicService", "Failed to commit prefs migration for $PREFS_NAME — will retry on next launch", e)
                    }
                }
                prefsInstance = encryptedPrefs
                return encryptedPrefs
            }

            // Keystore unavailable — fall back to standard (unencrypted) preferences.
            // If a migration was previously completed, the standard prefs will be empty;
            // the user will just need to reconfigure settings.
            prefsInstance = standardPrefs
            return standardPrefs
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
        internal const val ALBUM_ART_TARGET_SIZE_PX = 512
        private const val FOLDER_ART_CACHE_MAX_KB = 8 * 1024

        private const val NOW_PLAYING_CHANNEL_ID = "now_playing"
        private const val NOW_PLAYING_NOTIFICATION_ID = 1001

        private const val ANDROID_AUTO_PACKAGE_NAME = "com.google.android.projection.gearhead"

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
    private var pendingQueueRestoreUris: List<String>? = null
    private var repeatMode: Int = PlaybackStateCompat.REPEAT_MODE_NONE
    private var pendingResumePositionMs: Long? = null
    private var resumeOnAudioFocusGain: Boolean = false
    private var isDuckedForFocusLoss: Boolean = false
    private var lastBrowseParentId: String? = null
    private var lastSearchQuery: String? = null
    private var lastSearchResults: List<MediaFileInfo> = emptyList()
    private var consecutivePlaybackErrors: Int = 0
    // Claimed atomically by tryBeginScan() so two concurrent callers of
    // loadCachedTreeIfAvailable() can never both pass the guard and each build
    // their own full-size cache (see issue #382).
    private val scanGuard = AtomicBoolean(false)
    private val isScanning: Boolean
        get() = scanGuard.get()
    private var isForegroundPromoted: Boolean = false
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
            Log.d("MyMusicService", "callback.onPlay()")
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
            Log.d("MyMusicService", "callback.onPlayFromMediaId(mediaId=$mediaId)")
            val resolvedMediaId = mediaId ?: return
            playJob?.cancel()
            playJob = serviceScope.launch {
                playMutex.withLock {
                    Log.d("MyMusicService", "callback.onPlayFromMediaId: invoking handler for $resolvedMediaId")
                    handlePlayFromMediaId(resolvedMediaId)
                    Log.d("MyMusicService", "callback.onPlayFromMediaId: handler returned for $resolvedMediaId")
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
            Log.d("MyMusicService", "callback.onCustomAction(action=$action)")

            // Validate that custom actions are coming from an allowed client.
            // In MediaSessionCompat.Callback, we can get the remote client info directly
            val remoteUserInfo = session.currentControllerInfo
            val callerUid = remoteUserInfo.uid
            val callerPackageName = remoteUserInfo.packageName
            val validator = PackageValidator(this@MyMusicService)
            if (!validator.isCallerValid(callerPackageName, callerUid)) {
                Log.w("MyMusicService", "Rejecting custom action $action from unauthorized package $callerPackageName")
                return
            }

            when (action) {
                ACTION_SET_MEDIA_FILES -> handleSetMediaFiles(extras)
                ACTION_REFRESH_LIBRARY -> loadCachedTreeIfAvailable()
                ACTION_PLAY_SEARCH_LIST -> handlePlaySearchList(extras)
                ACTION_PLAY_UI_LIST -> handlePlayUiList(extras)
                ACTION_SET_PLAYLISTS -> handleSetPlaylists(extras)
                ACTION_SET_TRACK_VOICE_INTRO -> handleSetTrackVoiceIntro(extras)
                ACTION_SET_TRACK_VOICE_OUTRO -> handleSetTrackVoiceOutro(extras)
                ACTION_SET_DEBUG_CLOUD -> handleSetDebugCloud(extras)
                CUSTOM_ACTION_FLAG -> handleCustomActionFlag()
            }
        }

        private fun handleSetMediaFiles(extras: Bundle?) {
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
            val mappedFiles = ArrayList<MediaFileInfo>(count)
            for (i in 0 until count) {
                val title = titles?.getOrNull(i).orEmpty().ifBlank { names[i].substringBeforeLast('.') }
                val artist = artists?.getOrNull(i).orEmpty().ifBlank { null }
                val album = albums?.getOrNull(i).orEmpty().ifBlank { null }
                val genre = genres?.getOrNull(i).orEmpty().ifBlank { null }
                val durationMs = durations?.getOrNull(i)?.takeIf { it >= 0L }
                val year = years?.getOrNull(i)?.takeIf { it > 0 }
                val addedAtMs = addedAt?.getOrNull(i)?.takeIf { it >= 0L }
                mappedFiles.add(
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
            mediaCacheService.addAllFiles(mappedFiles)
            serviceScope.launch {
                val standardPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val treeUriStr = standardPrefs.getString(KEY_TREE_URI, null)
                if (treeUriStr != null) {
                    val limit = standardPrefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
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

        private fun handlePlaySearchList(extras: Bundle?) {
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

        private fun handlePlayUiList(extras: Bundle?) {
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

        private fun handleSetPlaylists(extras: Bundle?) {
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

        private fun handleSetTrackVoiceIntro(extras: Bundle?) {
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

        private fun handleSetTrackVoiceOutro(extras: Bundle?) {
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

        private fun handleSetDebugCloud(extras: Bundle?) {
            val enabled = extras?.getBoolean(EXTRA_DEBUG_CLOUD_ENABLED) ?: false
            debugCloudAnnouncementsEnabled = enabled
            getPrefs(this@MyMusicService)
                .edit()
                .putBoolean(KEY_DEBUG_CLOUD_ANNOUNCEMENTS, enabled)
                .apply()
        }

        private fun handleCustomActionFlag() {
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

    override fun onCreate() {
        val t0 = SystemClock.elapsedRealtime()
        fun mark(step: String) {
            Log.d("MyMusicService", "onCreate[+${SystemClock.elapsedRealtime() - t0}ms] $step")
        }
        mark("enter")
        super.onCreate()
        mark("super.onCreate done")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = NotificationManagerCompat.from(this)
        ensureNotificationChannel()
        mark("notification setup done")

        session = MediaSessionCompat(this, "MyMusicService")
        session.setSessionActivity(buildLaunchIntent())
        sessionToken = session.sessionToken
        session.setCallback(callback)
        @Suppress("DEPRECATION")
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mark("session setup done")

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
        mark("playback state set")

        trackVoiceIntroEnabled = getPrefs(this@MyMusicService)
            .getBoolean(KEY_TRACK_VOICE_INTRO_ENABLED, false)
        trackVoiceOutroEnabled = getPrefs(this@MyMusicService)
            .getBoolean(KEY_TRACK_VOICE_OUTRO_ENABLED, false)
        debugCloudAnnouncementsEnabled = getPrefs(this@MyMusicService)
            .getBoolean(KEY_DEBUG_CLOUD_ANNOUNCEMENTS, false)
        mark("prefs read (intro=$trackVoiceIntroEnabled outro=$trackVoiceOutroEnabled debug=$debugCloudAnnouncementsEnabled)")
        if (trackVoiceIntroEnabled || trackVoiceOutroEnabled) {
            ensureTextToSpeechInitialized()
            mark("TTS init done")
        }
        announcementPreGenerator = AnnouncementPreGenerator(this, serviceScope)
        mark("announcementPreGenerator built")

        restorePlaybackSnapshot()
        mark("restorePlaybackSnapshot done")
        loadCachedTreeIfAvailable()
        mark("loadCachedTreeIfAvailable returned (isScanning=$isScanning, cachedFiles=${mediaCacheService.cachedFilesCount})")
        mark("exit")
    }

    // Promote the service to a foreground service so Android (and Samsung's
    // "Freecess" aggressive freezer in particular) cannot freeze the process
    // before the user's first play command arrives. Without this, gearhead's
    // binder transactions to our MediaSession fail with error -32 ("sent binder
    // to frozen apps"), and AA's spinner ("getting your selection") hangs
    // forever. Only called for AA connections (see onGetRoot) so phone-only
    // sessions don't show a persistent "now playing" notification.
    private fun startForegroundReady() {
        runCatching {
            val notification = buildNowPlayingNotification(PlaybackStateCompat.STATE_NONE)
            startForeground(NOW_PLAYING_NOTIFICATION_ID, notification)
        }.onFailure {
            Log.w("MyMusicService", "Failed to start foreground service", it)
        }
    }

    private fun playProvidedUriList(
        uris: List<String>,
        shuffle: Boolean,
        queueTitle: String,
        setSearchResults: Boolean
    ) {
        if (uris.isEmpty()) return
        // Fresh play/shuffle — discard any resume position carried over from a
        // previous snapshot so the first track starts at the beginning (#384).
        pendingResumePositionMs = null
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            mediaCacheService.trimMemory()
        }
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
            val rawQuery = try {
                intent.getStringExtra(SearchManager.QUERY) ?: ""
            } catch (e: Exception) {
                Log.w("MyMusicService", "Failed to read search query extra", e)
                ""
            }
            val truncatedQuery = if (rawQuery.length > 500) {
                rawQuery.substring(0, 500)
            } else {
                rawQuery
            }
            val query = truncatedQuery.replace(SEARCH_INTENT_SANITIZE_REGEX, "")

            val extras = Bundle().apply {
                val allowedKeys = setOf(
                    "android.intent.extra.focus",
                    "android.intent.extra.artist",
                    "android.intent.extra.album",
                    "android.intent.extra.genre",
                    "android.intent.extra.title",
                    "android.intent.extra.playlist"
                )
                for (key in allowedKeys) {
                    try {
                        val value = intent.getStringExtra(key)
                        if (value != null) {
                            val truncatedValue = if (value.length > 500) value.substring(0, 500) else value
                            val sanitizedValue = truncatedValue.replace(SEARCH_INTENT_SANITIZE_REGEX, "")
                            putString(key, sanitizedValue)
                        }
                    } catch (e: Exception) {
                        Log.w("MyMusicService", "Failed to read search intent extra '$key'", e)
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
    ): MediaBrowserServiceCompat.BrowserRoot? {
        Log.d("MyMusicService", "onGetRoot from $clientPackageName uid=$clientUid")

        if (clientPackageName == ANDROID_AUTO_PACKAGE_NAME && !isForegroundPromoted) {
            startForegroundReady()
            isForegroundPromoted = true
        }

        val validator = PackageValidator(this)
        if (!validator.isCallerValid(clientPackageName, clientUid)) {
            Log.w("MyMusicService", "Rejecting connection from unauthorized package $clientPackageName")
            return null
        }

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
        Log.d("MyMusicService", "onLoadChildren($parentId) isScanning=$isScanning cachedFiles=${mediaCacheService.cachedFilesCount}")
        // No direct integration test for this gate: MediaBrowserServiceCompat.Result
        // has a package-private constructor and cannot be subclassed from our test
        // sources without a fake in androidx.media. The two operands are tested
        // independently — parentRequiresLoadedCache via parentRequiresLoadedCache_…
        // in MyMusicServiceTest; the isScanning lifecycle via loadCachedTreeIfAvailable.
        if (isScanning && parentRequiresLoadedCache(parentId)) {
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
                    result.sendResult(buildMediaItems(parentId).toMutableList())
                } catch (e: Exception) {
                    Log.e("MyMusicService", "Failed to build media items for $parentId", e)
                    result.sendResult(mutableListOf())
                }
            }
            return
        }

        result.sendResult(buildMediaItems(parentId).toMutableList())
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
        recordSearchQuery(trimmed)
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

    private fun recordSearchQuery(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val history = (listOf(normalized) + readSearchHistory())
            .distinctBy { it.lowercase() }
            .take(MAX_SEARCH_HISTORY)
        persistSearchHistory(history)
        notifyChildrenChanged(SEARCH_ID)
    }

    private fun readSearchHistory(): List<String> {
        val raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SEARCH_HISTORY, null)
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(MAX_SEARCH_HISTORY)
            .toList()
    }

    private fun persistSearchHistory(history: List<String>) {
        val encoded = history
            .map { it.replace('\n', ' ').replace('\t', ' ').trim() }
            .filter { it.isNotBlank() }
            .take(MAX_SEARCH_HISTORY)
            .joinToString("\n")
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SEARCH_HISTORY, encoded)
            .apply()
    }

    private fun buildMediaItemsForPrefix(parentId: String): List<MediaItem>? {
        return when {
            parentId.startsWith(SEARCH_QUERY_PREFIX) -> buildMediaItemsForSearchQuery(parentId)
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

    private fun buildMediaItemsForSmartPlaylist(parentId: String): List<MediaItem> {
            val smartId = Uri.decode(parentId.removePrefix(SMART_PLAYLIST_PREFIX))
            val tracks = resolveSmartPlaylistTracksById(smartId) ?: emptyList()
            return buildSongListItems(
                tracks,
                SMART_PLAYLIST_PREFIX + Uri.encode(smartId),
                resourceIconUri(R.drawable.ic_album_placeholder)
            )
    }

    private fun buildMediaItemsForPlaylist(parentId: String): List<MediaItem> {
            val shortId = parentId.removePrefix(PLAYLIST_PREFIX)
            val playlistUri = playlistShortIds[shortId] ?: return emptyList()
            val songs = enrichFromCache(
                playlistService.readPlaylist(this, Uri.parse(playlistUri))
            )
            val songIconUri = resourceIconUri(R.drawable.ic_album_placeholder)
            return buildSongListItems(songs, PLAYLIST_SHORT_PREFIX + shortId, songIconUri)
    }

    private fun buildMediaItemsForAlbum(parentId: String): List<MediaItem> {
            ensureMetadataIndexes()
            val album = Uri.decode(parentId.removePrefix(ALBUM_PREFIX))
            return buildSongListItems(mediaCacheService.songsForAlbum(album), ALBUM_PREFIX + Uri.encode(album), resourceIconUri(R.drawable.ic_album_placeholder))
    }

    private fun buildMediaItemsForGenre(parentId: String): List<MediaItem> {
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

    private fun buildMediaItemsForGenreSongLetter(parentId: String): List<MediaItem> {
            ensureMetadataIndexes()
            val parts = parseBucketParts(parentId, GENRE_SONG_LETTER_PREFIX) ?: return emptyList()
            val genre = parts.first
            val letter = parts.second
            val songs = mediaCacheService.songsForGenre(genre)
            val filtered = filterSongsByLetter(songs, letter)
            return buildSongLetterItems(
                GENRE_SONG_LETTER_PREFIX + Uri.encode(genre) + ":" + Uri.encode(letter),
                filtered
            )
    }

    private fun buildMediaItemsForArtist(parentId: String): List<MediaItem> {
            ensureMetadataIndexes()
            val artist = Uri.decode(parentId.removePrefix(ARTIST_PREFIX))
            return buildSongListItems(mediaCacheService.songsForArtist(artist), ARTIST_PREFIX + Uri.encode(artist), resourceIconUri(R.drawable.ic_album_placeholder))
    }

    private fun buildMediaItemsForArtistLetter(parentId: String): List<MediaItem> {
            ensureMetadataIndexes()
            val letter = Uri.decode(parentId.removePrefix(ARTIST_LETTER_PREFIX))
            return buildCategoryListItems(
                filterByLetter(mediaCacheService.artists(), letter),
                ARTIST_PREFIX,
                buildArtistCounts(mediaCacheService.cachedMusicFiles),
                resourceIconUri(R.drawable.ic_auto_artists)
            )
    }

    private fun buildMediaItemsForDecade(parentId: String): List<MediaItem> {
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

    private fun buildMediaItemsForDecadeSongLetter(parentId: String): List<MediaItem> {
            ensureMetadataIndexes()
            val parts = parseBucketParts(parentId, DECADE_SONG_LETTER_PREFIX) ?: return emptyList()
            val decade = parts.first
            val letter = parts.second
            val songs = mediaCacheService.songsForDecade(decade)
            val filtered = filterSongsByLetter(songs, letter)
            return buildSongLetterItems(
                DECADE_SONG_LETTER_PREFIX + Uri.encode(decade) + ":" + Uri.encode(letter),
                filtered
            )
    }

    private fun buildMediaItemsForGenreLetter(parentId: String): List<MediaItem> {
            ensureMetadataIndexes()
            val letter = Uri.decode(parentId.removePrefix(GENRE_LETTER_PREFIX))
            return buildCategoryListItems(
                filterByLetter(mediaCacheService.genres(), letter),
                GENRE_PREFIX,
                iconUri = resourceIconUri(R.drawable.ic_auto_genres)
            )
    }

    private fun buildMediaItemsForSongLetter(parentId: String): List<MediaItem> {
            val letter = Uri.decode(parentId.removePrefix(SONG_LETTER_PREFIX))
            val filtered = filterSongsByLetter(mediaCacheService.cachedMusicFiles, letter)
            return buildSongLetterItems(
                SONG_LETTER_PREFIX + Uri.encode(letter),
                filtered
            )
    }

    private fun buildMediaItemsForId(parentId: String): List<MediaItem> {
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

    private fun buildRootItems(): List<MediaItem> {
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

    private fun buildSongsItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        if (mediaCacheService.cachedMusicFiles.isNotEmpty()) {
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

    private fun buildSongsAllItems(): List<MediaItem> {
        val titles = mediaCacheService.cachedMusicFiles.map { it.cleanTitle }
        return buildCategoryListItems(
            buildLetterBuckets(titles),
            SONG_LETTER_PREFIX,
            iconUri = resourceIconUri(R.drawable.ic_auto_song)
        )
    }

    private fun buildPlaylistsItems(): List<MediaItem> {
        return playlistEntriesForBrowse(mediaCacheService.discoveredPlaylists).map { entry ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(entry.mediaId)
                .setTitle(entry.title)
                .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                .build()
            MediaItem(description, MediaItem.FLAG_BROWSABLE)
        }
    }

    private fun buildAlbumsItems(): List<MediaItem> {
        ensureMetadataIndexes()
        return buildCategoryListItems(
            mediaCacheService.albums(),
            ALBUM_PREFIX,
            buildAlbumCounts(mediaCacheService.cachedMusicFiles),
            resourceIconUri(R.drawable.ic_auto_albums)
        )
    }

    private fun buildGenresItems(): List<MediaItem> {
        ensureMetadataIndexes()
        return buildCategoryListItems(
            mediaCacheService.genres(),
            GENRE_PREFIX,
            buildGenreCounts(mediaCacheService.cachedFiles),
            resourceIconUri(R.drawable.ic_auto_genres)
        )
    }

    private fun buildArtistsItems(): List<MediaItem> {
        ensureMetadataIndexes()
        return buildCategoryListItems(
            buildLetterBuckets(mediaCacheService.artists()),
            ARTIST_LETTER_PREFIX,
            iconUri = resourceIconUri(R.drawable.ic_auto_artists)
        )
    }

    private fun buildDecadesItems(): List<MediaItem> {
        ensureMetadataIndexes()
        return buildCategoryListItems(
            mediaCacheService.decades(),
            DECADE_PREFIX,
            buildDecadeCounts(mediaCacheService.cachedMusicFiles),
            resourceIconUri(R.drawable.ic_auto_decades)
        )
    }

    private fun buildSearchItems(): List<MediaItem> {
        val history = readSearchHistory()
        if (history.isEmpty()) {
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
        return history.map { query ->
            MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(SEARCH_QUERY_PREFIX + Uri.encode(query))
                    .setTitle(query)
                    .setSubtitle("Recent search")
                    .setIconUri(resourceIconUri(R.drawable.ic_auto_song))
                    .build(),
                MediaItem.FLAG_BROWSABLE
            )
        }
    }

    private fun buildMediaItemsForSearchQuery(parentId: String): List<MediaItem> {
        val query = Uri.decode(parentId.removePrefix(SEARCH_QUERY_PREFIX)).trim()
        if (query.isBlank()) return emptyList()
        val matches = mediaCacheService.searchFiles(query)
        if (matches.isEmpty()) {
            return listOf(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("search_empty")
                        .setTitle("No results for \"$query\"")
                        .build(),
                    MediaItem.FLAG_BROWSABLE
                )
            )
        }
        return buildPlayAllShuffleItems(SEARCH_QUERY_PREFIX + Uri.encode(query)) +
            buildSongItems(matches, resourceIconUri(R.drawable.ic_album_placeholder))
    }
    internal fun buildMediaItems(parentId: String): List<MediaItem> {
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
            val c = value.firstOrNull { !it.isWhitespace() }
            if (c != null) {
                val u = c.uppercaseChar()
                if (u in 'A'..'Z') {
                    seenLetters[u - 'A'] = true
                } else {
                    hasOther = true
                }
            } else {
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
            if (file.isPodcast) PODCAST_GENRE else bucketGenre(file.genre)
        }.eachCount()

    @VisibleForTesting
    internal fun buildSongLetterBuckets(songs: List<MediaFileInfo>): List<String> {
        val presentLetters = BooleanArray(26)
        var hasOther = false
        for (song in songs) {
            val firstChar = song.cleanTitle.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
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

    private fun buildPlayAllShuffleItems(listKey: String): List<MediaItem> {
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
    ): List<MediaItem> {
        return buildPlayAllShuffleItems(listKey) + buildCategoryListItems(
            buildSongLetterBuckets(songs),
            bucketPrefix,
            buildSongLetterCounts(songs),
            resourceIconUri(R.drawable.ic_auto_song)
        )
    }

    private fun buildSongLetterItems(
        listKey: String,
        songs: List<MediaFileInfo>
    ): List<MediaItem> {
        return buildPlayAllShuffleItems(listKey) +
            buildSongItems(songs, resourceIconUri(R.drawable.ic_album_placeholder))
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

    @VisibleForTesting
    internal fun tryBeginScan(): Boolean = scanGuard.compareAndSet(false, true)

    @VisibleForTesting
    internal fun endScan() {
        scanGuard.set(false)
    }

    private fun loadCachedTreeIfAvailable() {
        if (mediaCacheService.hasCachedFiles()) return
        // Claim the guard before any other work so two near-simultaneous callers
        // can never both pass and each build their own full-size cache (#382).
        if (!tryBeginScan()) return
        // Released in the finally below unless handedOffToCoroutine becomes true —
        // covers every early return *and* any unexpected exception in the
        // synchronous setup below, so the guard can never get stuck claimed.
        var handedOffToCoroutine = false
        try {
            // Read scan settings from standard prefs — the activity always writes to standard prefs,
            // but getPrefs() may return encrypted prefs (which can be stale after settings changes).
            val standardPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val limit = standardPrefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
            val wholeDriveMode = standardPrefs.getBoolean(KEY_SCAN_WHOLE_DRIVE, false)
            val uri = if (wholeDriveMode) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                val uriString = standardPrefs.getString(KEY_TREE_URI, null) ?: return
                val parsed = Uri.parse(uriString)
                val hasPermission = contentResolver.persistedUriPermissions.any {
                    it.uri == parsed && it.isReadPermission
                }
                if (!hasPermission) return
                parsed
            }

            folderArtCache.evictAll()
            folderArtNotFound.clear()

            handedOffToCoroutine = true
            serviceScope.launch {
                try {
                    val persisted = mediaCacheService.loadPersistedCache(this@MyMusicService, uri, limit)
                    val cacheLoadedFromDisk = persisted != null && persisted.files.isNotEmpty()
                    // Index build is deferred until first browse (see ensureMetadataIndexes()).
                    if (!cacheLoadedFromDisk) {
                        var lastNotify = 0
                        val progress: (Int, Int) -> Unit = { songsFound, _ ->
                            if (songsFound - lastNotify >= 200) {
                                lastNotify = songsFound
                                notifyChildrenChanged(SONGS_ALL_ID)
                            }
                        }
                        val scanStartedAt = System.currentTimeMillis()
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
                        mediaCacheService.persistCache(this@MyMusicService, uri, limit, scanStartedAt)
                    }
                } finally {
                    endScan()
                    resolvePendingQueueIfNeeded()
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
        } finally {
            if (!handedOffToCoroutine) endScan()
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
            val items = buildMediaItems(parentId).toMutableList()
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

    internal fun parentRequiresLoadedCache(parentId: String): Boolean {
        // Cache-independent: these screens render purely from static data,
        // so they MUST respond even while the service is loading its cache.
        // Returning a Result.detach() for the root tree past Android Auto's
        // onLoadChildren timeout causes "MyMediaPlayer doesn't seem to be
        // working at the moment."
        return when (parentId) {
            ROOT_ID, SEARCH_ID, HOME_ID -> false
            else -> true
        }
    }

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
                    serviceScope.launch {
                        updateMetadata(enrichedFileInfo)
                    }
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
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR, errorMessage = message)
            teardownPlayer()
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
        teardownPlayer()
        consecutivePlaybackErrors = 0
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
        // Fresh play/shuffle/play_all action — discard any resume position carried
        // over from a previous snapshot so the first track starts at the
        // beginning (#384). Subsequent onSkipToNext/Previous/QueueItem calls are
        // routed past this method and keep using pendingResumePositionMs normally.
        pendingResumePositionMs = null
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

    private suspend fun resolveTracksForListKey(listKey: String): List<MediaFileInfo> {
        return when {
            listKey == SONGS_ID -> mediaCacheService.cachedMusicFiles
            listKey.startsWith(SEARCH_QUERY_PREFIX) -> {
                val query = Uri.decode(listKey.removePrefix(SEARCH_QUERY_PREFIX)).trim()
                if (query.isBlank()) emptyList() else mediaCacheService.searchFiles(query)
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
                filterSongsByLetter(mediaCacheService.cachedMusicFiles, letter)
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
    }

    private fun resolvePlaylistNameForListKey(listKey: String): String {
        return when {
            listKey == SONGS_ID -> "All Songs"
            listKey.startsWith(SEARCH_QUERY_PREFIX) -> {
                val query = Uri.decode(listKey.removePrefix(SEARCH_QUERY_PREFIX)).trim()
                if (query.isBlank()) "Search Results" else "Search: $query"
            }
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
    }

    private suspend fun handlePlayAllOrShuffle(resolvedMediaId: String) {
        val isShuffle = resolvedMediaId.startsWith(ACTION_SHUFFLE_PREFIX)
        val listKey = resolvedMediaId.removePrefix(
            if (isShuffle) ACTION_SHUFFLE_PREFIX else ACTION_PLAY_ALL_PREFIX
        )
        val tracks = resolveTracksForListKey(listKey)

        if (tracks.isEmpty()) {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }

        playQueueAndSaveSnapshot(tracks, isShuffle, resolvePlaylistNameForListKey(listKey))
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
        val listFromParent = getListFromParent(parentId)

        val searchList = if (lastSearchResults.any { it.uriString == resolvedMediaId }) {
            lastSearchResults
        } else {
            emptyList()
        }
        val parentList = when {
            listFromParent.isNotEmpty() -> listFromParent
            searchList.isNotEmpty() -> searchList
            mediaCacheService.cachedMusicFiles.isNotEmpty() -> mediaCacheService.cachedMusicFiles
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
                currentPlaylistName = getPlaylistNameForSingleItem(parentId, searchList.isNotEmpty(), listFromParent.isEmpty())
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

    private fun getListFromParent(parentId: String?): List<MediaFileInfo> {
        return when {
            parentId == SONGS_ID -> mediaCacheService.cachedMusicFiles
            parentId == SONGS_ALL_ID -> mediaCacheService.cachedMusicFiles
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
                filterSongsByLetter(mediaCacheService.cachedMusicFiles, letter)
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
    }

    private fun getPlaylistNameForSingleItem(parentId: String?, isFromSearch: Boolean, listFromParentIsEmpty: Boolean): String? {
        return when {
            isFromSearch -> {
                val query = lastSearchQuery?.trim().orEmpty()
                if (query.isNotEmpty()) "Search: $query" else "Search Results"
            }
            parentId == SONGS_ID || parentId == SONGS_ALL_ID || listFromParentIsEmpty -> "All Songs"
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

    private suspend fun handlePlayFromSearch(query: String?, extras: Bundle?) {
        // Fresh play from voice/search — discard any resume position carried over
        // from a previous snapshot so the first track starts at the beginning (#384).
        pendingResumePositionMs = null
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
        if (queryForSongs.isNotBlank()) {
            recordSearchQuery(queryForSongs)
        }
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

    private fun refine(
        base: List<MediaFileInfo>?,
        filter: (MediaFileInfo) -> Boolean
    ): List<MediaFileInfo> {
        val source = base ?: mediaCacheService.cachedFiles
        return source.filter(filter)
    }

    private fun findMatchesByExplicitFields(
        extras: Bundle?
    ): Pair<List<MediaFileInfo>?, String?> {
        val requestedArtist = extras?.getString(EXTRA_MEDIA_ARTIST_KEY)?.trim().orEmpty()
        val requestedAlbum = extras?.getString(EXTRA_MEDIA_ALBUM_KEY)?.trim().orEmpty()
        val requestedGenre = extras?.getString(EXTRA_MEDIA_GENRE_KEY)?.trim().orEmpty()
        val requestedTitle = extras?.getString(EXTRA_MEDIA_TITLE_KEY)?.trim().orEmpty()

        var focusedMatches: List<MediaFileInfo>? = null
        var focusLabel: String? = null

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

        return Pair(focusedMatches, focusLabel)
    }

    private fun findMatchesByFocus(
        focusQuery: String,
        mediaFocus: String
    ): Pair<List<MediaFileInfo>?, String?> {
        if (focusQuery.isBlank()) return Pair(null, null)

        val focusArtist = mediaFocus.equals(FOCUS_ARTIST, ignoreCase = true)
        val focusAlbum = mediaFocus.equals(FOCUS_ALBUM, ignoreCase = true)
        val focusGenre = mediaFocus.equals(FOCUS_GENRE, ignoreCase = true)
        val focusTitle = mediaFocus.equals(FOCUS_TITLE, ignoreCase = true)

        val focusedMatches = when {
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
        val focusLabel = when {
            focusArtist -> "Artist: $focusQuery"
            focusAlbum -> "Album: $focusQuery"
            focusGenre -> "Genre: $focusQuery"
            focusTitle -> "Title: $focusQuery"
            else -> null
        }
        return Pair(focusedMatches, focusLabel)
    }

    private fun findFocusedMatches(
        raw: String,
        cleanedQuery: String,
        extras: Bundle?
    ): Pair<List<MediaFileInfo>?, String?> {
        val (explicitMatches, explicitLabel) = findMatchesByExplicitFields(extras)
        if (explicitMatches != null) {
            return Pair(explicitMatches, explicitLabel)
        }

        val mediaFocus = extras?.getString(EXTRA_MEDIA_FOCUS_KEY)?.trim().orEmpty()
        val focusQuery = cleanedQuery.ifBlank { raw }.trim()

        return findMatchesByFocus(focusQuery, mediaFocus)
    }

    private suspend fun ensureCacheReadyForSearch() {
        if (mediaCacheService.hasCachedFiles()) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
        val uri = Uri.parse(uriString)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPermission) return
        withContext(Dispatchers.IO) {
            mediaCacheService.loadPersistedCache(this@MyMusicService, uri, limit)
        }
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
        val all = mediaCacheService.cachedMusicFiles.ifEmpty { playlistQueue }
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

    private fun teardownPlayer() {
        resumeOnAudioFocusGain = false
        val lastPosition = currentPositionSafeMs()
        releaseMediaPlayer()
        abandonAudioFocus()
        savePlaybackSnapshot(positionMsOverride = lastPosition)
    }

    private fun handleStop() {
        teardownPlayer()
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        consecutivePlaybackErrors = 0
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

    private suspend fun updateMetadata(fileInfo: MediaFileInfo) {
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
                retriever.embeddedPicture?.let { decodeSampledBitmapFromBytes(it) }
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
        }.onFailure { Timber.w(it, "Failed to read embedded art via MMR") }.getOrNull()

        val albumArtBitmap = if (embeddedArtBitmap != null) {
            embeddedArtBitmap
        } else {
            val media3Art = runCatching {
                extractArtworkFromMedia3(this@MyMusicService, fileInfo.uriString)?.let { artBytes ->
                    decodeSampledBitmapFromBytes(artBytes)
                }
            }.onFailure { Timber.w(it, "Failed to read embedded art via media3") }.getOrNull()
            media3Art ?: runCatching {
                Mp4CoverExtractor.extractCoverArt(this@MyMusicService, fileInfo.uriString)?.let { artBytes ->
                    decodeSampledBitmapFromBytes(artBytes)
                }
            }.onFailure { Timber.w(it, "Failed to read art from MP4 atoms") }.getOrNull()
            ?: runCatching {
                extractFolderArt(this@MyMusicService, Uri.parse(fileInfo.uriString))
            }.onFailure { Timber.w(it, "Failed to read folder art") }.getOrNull()
            ?: loadPlaceholderArt()
        }

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

    @OptIn(markerClass = [UnstableApi::class])
    private suspend fun extractArtworkFromMedia3(context: Context, uriString: String): ByteArray? {
        return try {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(uriString)
            MetadataRetriever.Builder(context, mediaItem).build().use { retriever ->
                val trackGroups = retriever.retrieveTrackGroups().await()
                for (i in 0 until trackGroups.length) {
                    val trackGroup = trackGroups[i]
                    for (j in 0 until trackGroup.length) {
                        val metadata = trackGroup.getFormat(j).metadata ?: continue
                        for (k in 0 until metadata.length()) {
                            when (val entry = metadata[k]) {
                                is ApicFrame -> return@use entry.pictureData
                                is PictureFrame -> return@use entry.pictureData
                            }
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    @VisibleForTesting
    internal fun calculateAlbumArtInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int = ALBUM_ART_TARGET_SIZE_PX,
        reqHeight: Int = ALBUM_ART_TARGET_SIZE_PX
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            while (height / (inSampleSize * 2) >= reqHeight || width / (inSampleSize * 2) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateAlbumArtInSampleSize(boundsOptions)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    @VisibleForTesting
    internal fun decodeSampledBitmapFromStream(openInputStream: () -> InputStream?): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = openInputStream() ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateAlbumArtInSampleSize(boundsOptions)
        }
        return openInputStream()?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }

    // LruCache for found folder-art bitmaps; separate set tracks folders with no art
    // to avoid repeated SAF lookups. Both are cleared when the library is rescanned.
    private val folderArtCache = object : LruCache<String, Bitmap>(FOLDER_ART_CACHE_MAX_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val folderArtNotFound = mutableSetOf<String>()

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

    private fun extractFolderArt(context: Context, trackUri: Uri): Bitmap? {
        val docId = try { DocumentsContract.getDocumentId(trackUri) } catch (_: Exception) { return null }
        val parentDocId = docId.substringBeforeLast('/')
        if (parentDocId == docId) return null // track is at tree root, no parent folder

        if (folderArtNotFound.contains(parentDocId)) return null
        folderArtCache[parentDocId]?.let { return it }

        for (name in FOLDER_ART_CANDIDATES) {
            val siblingUri = try {
                DocumentsContract.buildDocumentUriUsingTree(trackUri, "$parentDocId/$name")
            } catch (_: Exception) { continue }
            val bitmap = try {
                decodeSampledBitmapFromStream {
                    context.contentResolver.openInputStream(siblingUri)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to open folder art: $siblingUri")
                null
            }
            if (bitmap != null) {
                folderArtCache.put(parentDocId, bitmap)
                return bitmap
            }
        }

        folderArtNotFound.add(parentDocId)
        return null
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

    internal fun buildHomeItems(): List<MediaItem> {
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
        val songCount = mediaCacheService.cachedMusicFiles.size
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

    @VisibleForTesting
    internal fun resolvePendingQueueIfNeeded() {
        val uris = pendingQueueRestoreUris ?: return
        pendingQueueRestoreUris = null
        val savedIndex = currentQueueIndex
        resolveQueueUris(uris, savedIndex)
        updateSessionQueue()
    }

    @VisibleForTesting
    internal fun currentPlaylistQueue(): List<MediaFileInfo> = playlistQueue

    @VisibleForTesting
    internal fun hasPendingQueueRestore(): Boolean = pendingQueueRestoreUris != null

    @VisibleForTesting
    internal fun seedPendingQueueRestoreForTesting(uris: List<String>, index: Int) {
        pendingQueueRestoreUris = uris
        currentQueueIndex = index
    }

    private fun resolveQueueUris(uris: List<String>, savedIndex: Int) {
        val byUri = mediaCacheService.getFileIndexByUri()
        playlistQueue = uris.map { uri ->
            byUri[uri] ?: MediaFileInfo(
                uriString = uri,
                displayName = uri,
                sizeBytes = 0L,
                title = uri
            )
        }
        val clampedIndex = savedIndex.coerceIn(-1, playlistQueue.lastIndex)
        currentQueueIndex = if (clampedIndex >= 0) clampedIndex else 0
    }

    @VisibleForTesting
    internal fun restorePlaybackSnapshot() {
        val prefs = getPrefs(this@MyMusicService)
        repeatMode = prefs.getInt(KEY_RESUME_REPEAT_MODE, PlaybackStateCompat.REPEAT_MODE_NONE)
        session.setRepeatMode(repeatMode)

        val queueUrisRaw = prefs.getString(KEY_RESUME_QUEUE_URIS, null).orEmpty()
        val queueUris = if (queueUrisRaw.isBlank()) {
            emptyList()
        } else {
            queueUrisRaw.split('\n').filter { it.isNotBlank() }
        }
        if (queueUris.isNotEmpty() && mediaCacheService.hasCachedFiles()) {
            resolveQueueUris(queueUris, prefs.getInt(KEY_RESUME_QUEUE_INDEX, -1))
            currentPlaylistName = prefs.getString(KEY_RESUME_QUEUE_TITLE, null)
            updateSessionQueue()
        } else if (queueUris.isNotEmpty()) {
            // Cache isn't loaded yet (the common cold-start case) — every lookup
            // below would miss, so resolving now would build a full-size queue of
            // placeholder MediaFileInfo (title = raw URI) and ship it over Binder
            // via updateSessionQueue(), duplicating the real cache load that's
            // about to run in loadCachedTreeIfAvailable(). Defer until that
            // finishes (see resolvePendingQueueIfNeeded()), so this resolves once,
            // with real data (#382).
            pendingQueueRestoreUris = queueUris
            currentQueueIndex = prefs.getInt(KEY_RESUME_QUEUE_INDEX, -1)
            currentPlaylistName = prefs.getString(KEY_RESUME_QUEUE_TITLE, null)
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

    @VisibleForTesting
    internal fun savePlaybackSnapshot(positionMsOverride: Long? = null) {
        val currentUri = currentMediaId ?: currentFileInfo?.uriString
        val position = positionMsOverride ?: currentPositionSafeMs()
        val editor = getPrefs(this@MyMusicService)
            .edit()
            .putString(KEY_RESUME_MEDIA_URI, currentUri)
            .putLong(KEY_RESUME_POSITION_MS, position)
            .putInt(KEY_RESUME_REPEAT_MODE, repeatMode)
        // While a queue restore is still pending (#382), playlistQueue is
        // deliberately empty — writing it now would permanently overwrite the
        // real saved queue still sitting in prefs before it's ever resolved
        // (e.g. if onDestroy() or onPlay() triggers a snapshot save mid cold-start).
        // Leave the existing KEY_RESUME_QUEUE_* values untouched until resolved.
        if (pendingQueueRestoreUris == null) {
            editor
                .putString(KEY_RESUME_QUEUE_URIS, playlistQueue.joinToString("\n") { it.uriString })
                .putInt(KEY_RESUME_QUEUE_INDEX, currentQueueIndex)
                .putString(KEY_RESUME_QUEUE_TITLE, currentPlaylistName)
        }
        editor.apply()
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
