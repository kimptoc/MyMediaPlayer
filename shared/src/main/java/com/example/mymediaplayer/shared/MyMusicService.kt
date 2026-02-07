package com.example.mymediaplayer.shared

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

class MyMusicService : MediaBrowserServiceCompat() {

    companion object {
        private const val ROOT_ID = "root"
        private const val SONGS_ID = "songs"
        private const val PLAYLISTS_ID = "playlists"
        private const val ALBUMS_ID = "albums"
        private const val GENRES_ID = "genres"
        private const val ARTISTS_ID = "artists"
        private const val PLAYLIST_PREFIX = "playlist:"
        private const val ALBUM_PREFIX = "album:"
        private const val GENRE_PREFIX = "genre:"
        private const val ARTIST_PREFIX = "artist:"
        private const val PREFS_NAME = "mymediaplayer_prefs"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_SCAN_LIMIT = "scan_limit"

        private const val ACTION_SET_MEDIA_FILES = "SET_MEDIA_FILES"
        private const val ACTION_SET_PLAYLISTS = "SET_PLAYLISTS"

        private const val EXTRA_URIS = "uris"
        private const val EXTRA_NAMES = "names"
        private const val EXTRA_SIZES = "sizes"
        private const val EXTRA_PLAYLIST_URIS = "playlist_uris"
        private const val EXTRA_PLAYLIST_NAMES = "playlist_names"
    }

    private lateinit var session: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    private val mediaCacheService = MediaCacheService()
    private val playlistService = PlaylistService()
    private var currentFileInfo: MediaFileInfo? = null
    private var currentMediaId: String? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val playbackStateBuilder = PlaybackStateCompat.Builder()

    private var playlistQueue: List<MediaFileInfo> = emptyList()
    private var currentQueueIndex: Int = -1
    private var currentPlaylistName: String? = null
    @Volatile
    private var isScanning: Boolean = false
    private val pendingResults =
        mutableMapOf<String, MutableList<MediaBrowserServiceCompat.Result<MutableList<MediaItem>>>>()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> handleStop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> handlePause()
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
                    mediaPlayer?.start()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }
            }
        }
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (mediaPlayer == null) return
            if (!requestAudioFocus()) return
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val resolvedMediaId = mediaId ?: return
            if (resolvedMediaId.startsWith(PLAYLIST_PREFIX)) {
                val playlistUri = resolvedMediaId.removePrefix(PLAYLIST_PREFIX)
                val playlistInfo = mediaCacheService.discoveredPlaylists.firstOrNull {
                    it.uriString == playlistUri
                } ?: return
                val playlistTracks = playlistService.readPlaylist(
                    this@MyMusicService,
                    Uri.parse(playlistInfo.uriString)
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
                return
            }

            val fileInfo = mediaCacheService.cachedFiles.firstOrNull {
                it.uriString == resolvedMediaId
            } ?: run {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                return
            }

            playlistQueue = emptyList()
            currentQueueIndex = -1
            currentPlaylistName = null
            updateSessionQueue()
            playTrack(fileInfo)
        }

        override fun onPause() {
            handlePause()
        }

        override fun onStop() {
            handleStop()
        }

        override fun onSkipToNext() {
            if (playlistQueue.isEmpty()) return
            val nextIndex = currentQueueIndex + 1
            if (nextIndex >= playlistQueue.size) {
                handleStop()
                return
            }
            currentQueueIndex = nextIndex
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
        }

        override fun onSkipToPrevious() {
            if (playlistQueue.isEmpty()) return
            val previousIndex = currentQueueIndex - 1
            if (previousIndex < 0) return
            currentQueueIndex = previousIndex
            updateSessionQueue()
            playTrack(playlistQueue[currentQueueIndex])
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                ACTION_SET_MEDIA_FILES -> {
                    if (extras == null) return
                    val uris = extras.getStringArrayList(EXTRA_URIS) ?: return
                    val names = extras.getStringArrayList(EXTRA_NAMES) ?: return
                    val sizes = extras.getLongArray(EXTRA_SIZES) ?: return

                    mediaCacheService.clearFiles()
                    val count = minOf(uris.size, names.size, sizes.size)
                    for (i in 0 until count) {
                        mediaCacheService.addFile(
                            MediaFileInfo(
                                uriString = uris[i],
                                displayName = names[i],
                                sizeBytes = sizes[i],
                                title = names[i]
                            )
                        )
                    }
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(SONGS_ID)
                    notifyChildrenChanged(ALBUMS_ID)
                    notifyChildrenChanged(GENRES_ID)
                    notifyChildrenChanged(ARTISTS_ID)
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
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        session = MediaSessionCompat(this, "MyMusicService")
        sessionToken = session.sessionToken
        session.setCallback(callback)
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
        session.isActive = true

        loadCachedTreeIfAvailable()
    }

    override fun onDestroy() {
        releaseMediaPlayer()
        abandonAudioFocus()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot {
        return MediaBrowserServiceCompat.BrowserRoot(ROOT_ID, null)
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

        result.sendResult(buildMediaItems(parentId))
    }

    private fun buildMediaItems(parentId: String): MutableList<MediaItem> {
        if (parentId.startsWith(ALBUM_PREFIX)) {
            ensureMetadataIndexes()
            val album = Uri.decode(parentId.removePrefix(ALBUM_PREFIX))
            return mediaCacheService.songsForAlbum(album).map { fileInfo ->
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(fileInfo.uriString)
                    .setTitle(fileInfo.displayName)
                    .build()
                MediaItem(description, MediaItem.FLAG_PLAYABLE)
            }.toMutableList()
        }
        if (parentId.startsWith(GENRE_PREFIX)) {
            ensureMetadataIndexes()
            val genre = Uri.decode(parentId.removePrefix(GENRE_PREFIX))
            return mediaCacheService.songsForGenre(genre).map { fileInfo ->
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(fileInfo.uriString)
                    .setTitle(fileInfo.displayName)
                    .build()
                MediaItem(description, MediaItem.FLAG_PLAYABLE)
            }.toMutableList()
        }
        if (parentId.startsWith(ARTIST_PREFIX)) {
            ensureMetadataIndexes()
            val artist = Uri.decode(parentId.removePrefix(ARTIST_PREFIX))
            return mediaCacheService.songsForArtist(artist).map { fileInfo ->
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(fileInfo.uriString)
                    .setTitle(fileInfo.displayName)
                    .build()
                MediaItem(description, MediaItem.FLAG_PLAYABLE)
            }.toMutableList()
        }

        return when (parentId) {
            ROOT_ID -> {
                val items = mutableListOf<MediaItem>()
                val songsDesc = MediaDescriptionCompat.Builder()
                    .setMediaId(SONGS_ID)
                    .setTitle("Songs")
                    .build()
                items.add(MediaItem(songsDesc, MediaItem.FLAG_BROWSABLE))

                if (mediaCacheService.discoveredPlaylists.isNotEmpty()) {
                    val playlistsDesc = MediaDescriptionCompat.Builder()
                        .setMediaId(PLAYLISTS_ID)
                        .setTitle("Playlists")
                        .build()
                    items.add(MediaItem(playlistsDesc, MediaItem.FLAG_BROWSABLE))
                }

                if (mediaCacheService.cachedFiles.isNotEmpty()) {
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(ALBUMS_ID)
                                .setTitle("Albums")
                                .build(),
                            MediaItem.FLAG_BROWSABLE
                        )
                    )
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(GENRES_ID)
                                .setTitle("Genres")
                                .build(),
                            MediaItem.FLAG_BROWSABLE
                        )
                    )
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(ARTISTS_ID)
                                .setTitle("Artists")
                                .build(),
                            MediaItem.FLAG_BROWSABLE
                        )
                    )
                }

                items
            }
            SONGS_ID -> {
                mediaCacheService.cachedFiles.map { fileInfo ->
                    val description = MediaDescriptionCompat.Builder()
                        .setMediaId(fileInfo.uriString)
                        .setTitle(fileInfo.displayName)
                        .build()
                    MediaItem(description, MediaItem.FLAG_PLAYABLE)
                }.toMutableList()
            }
            PLAYLISTS_ID -> {
                mediaCacheService.discoveredPlaylists.map { playlist ->
                    val description = MediaDescriptionCompat.Builder()
                        .setMediaId(PLAYLIST_PREFIX + playlist.uriString)
                        .setTitle(playlist.displayName.removeSuffix(".m3u"))
                        .build()
                    MediaItem(description, MediaItem.FLAG_PLAYABLE)
                }.toMutableList()
            }
            ALBUMS_ID -> {
                ensureMetadataIndexes()
                mediaCacheService.albums().map { album ->
                    val description = MediaDescriptionCompat.Builder()
                        .setMediaId(ALBUM_PREFIX + Uri.encode(album))
                        .setTitle(album)
                        .build()
                    MediaItem(description, MediaItem.FLAG_BROWSABLE)
                }.toMutableList()
            }
            GENRES_ID -> {
                ensureMetadataIndexes()
                mediaCacheService.genres().map { genre ->
                    val description = MediaDescriptionCompat.Builder()
                        .setMediaId(GENRE_PREFIX + Uri.encode(genre))
                        .setTitle(genre)
                        .build()
                    MediaItem(description, MediaItem.FLAG_BROWSABLE)
                }.toMutableList()
            }
            ARTISTS_ID -> {
                ensureMetadataIndexes()
                mediaCacheService.artists().map { artist ->
                    val description = MediaDescriptionCompat.Builder()
                        .setMediaId(ARTIST_PREFIX + Uri.encode(artist))
                        .setTitle(artist)
                        .build()
                    MediaItem(description, MediaItem.FLAG_BROWSABLE)
                }.toMutableList()
            }
            else -> mutableListOf()
        }
    }

    private fun loadCachedTreeIfAvailable() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
        val uri = Uri.parse(uriString)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPermission) return

        Thread {
            isScanning = true
            try {
                mediaCacheService.scanDirectory(this, uri, limit)
                mediaCacheService.buildMetadataIndexes(this)
            } finally {
                isScanning = false
                deliverPendingResults()
                notifyChildrenChanged(ROOT_ID)
                notifyChildrenChanged(SONGS_ID)
                notifyChildrenChanged(PLAYLISTS_ID)
                notifyChildrenChanged(ALBUMS_ID)
                notifyChildrenChanged(GENRES_ID)
                notifyChildrenChanged(ARTISTS_ID)
            }
        }.start()
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

    private fun ensureMetadataIndexes() {
        if (!mediaCacheService.hasMetadataIndexes()) {
            mediaCacheService.buildMetadataIndexes(this)
        }
    }

    private fun playTrack(fileInfo: MediaFileInfo) {
        releaseMediaPlayer()
        if (!requestAudioFocus()) {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }

        currentFileInfo = fileInfo
        currentMediaId = fileInfo.uriString

        try {
            val uri = Uri.parse(fileInfo.uriString)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@MyMusicService, uri)
                setOnPreparedListener {
                    start()
                    updateMetadata(fileInfo)
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }
                setOnCompletionListener {
                    onTrackCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                    releaseMediaPlayer()
                    abandonAudioFocus()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            releaseMediaPlayer()
            abandonAudioFocus()
        }
    }

    private fun onTrackCompleted() {
        if (playlistQueue.isNotEmpty()) {
            val nextIndex = currentQueueIndex + 1
            if (nextIndex < playlistQueue.size) {
                currentQueueIndex = nextIndex
                updateSessionQueue()
                playTrack(playlistQueue[currentQueueIndex])
            } else {
                handleStop()
            }
        } else {
            handleStop()
        }
    }

    private fun updateSessionQueue() {
        if (playlistQueue.isEmpty()) {
            session.setQueue(null)
            session.setQueueTitle(null)
            return
        }

        val queueItems = playlistQueue.mapIndexed { index, fileInfo ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(fileInfo.uriString)
                .setTitle(fileInfo.displayName)
                .build()
            MediaSessionCompat.QueueItem(description, index.toLong())
        }
        session.setQueue(queueItems)
        session.setQueueTitle(currentPlaylistName)
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

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        audioManager.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
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
        }
    }

    private fun handleStop() {
        releaseMediaPlayer()
        abandonAudioFocus()
        playlistQueue = emptyList()
        currentQueueIndex = -1
        currentPlaylistName = null
        session.setQueue(null)
        session.setQueueTitle(null)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
    }

    private fun updatePlaybackState(state: Int) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val speed = if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f
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

        val queueActions = if (playlistQueue.isNotEmpty() && currentQueueIndex >= 0) {
            var actions = 0L
            if (currentQueueIndex < playlistQueue.size - 1) {
                actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            }
            if (currentQueueIndex > 0) {
                actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            }
            actions
        } else {
            0L
        }

        playbackStateBuilder
            .setActions(baseActions or queueActions)
            .setState(state, position, speed, SystemClock.elapsedRealtime())

        if (playlistQueue.isNotEmpty() && currentQueueIndex >= 0) {
            playbackStateBuilder.setActiveQueueItemId(currentQueueIndex.toLong())
        } else {
            playbackStateBuilder.setActiveQueueItemId(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN)
        }

        session.setPlaybackState(playbackStateBuilder.build())
    }

    private fun updateMetadata(fileInfo: MediaFileInfo) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, fileInfo.uriString)
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                fileInfo.title ?: fileInfo.displayName
            )
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, fileInfo.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, fileInfo.album)
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                fileInfo.durationMs ?: 0L
            )
            .putLong(
                MediaMetadataCompat.METADATA_KEY_YEAR,
                (fileInfo.year ?: 0).toLong()
            )
            .build()
        session.setMetadata(metadata)
    }
}
