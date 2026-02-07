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
        private const val ACTION_SET_MEDIA_FILES = "SET_MEDIA_FILES"
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_NAMES = "names"
        private const val EXTRA_SIZES = "sizes"
    }

    private lateinit var session: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    private val mediaCacheService = MediaCacheService()
    private var currentFileInfo: MediaFileInfo? = null
    private var currentMediaId: String? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val playbackStateBuilder = PlaybackStateCompat.Builder()

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
            val fileInfo = mediaCacheService.cachedFiles.firstOrNull {
                it.uriString == resolvedMediaId
            } ?: run {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                return
            }

            releaseMediaPlayer()
            if (!requestAudioFocus()) {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                return
            }

            currentFileInfo = fileInfo
            currentMediaId = resolvedMediaId

            try {
                val uri = Uri.parse(resolvedMediaId)
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
                        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                        releaseMediaPlayer()
                        abandonAudioFocus()
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

        override fun onPause() {
            handlePause()
        }

        override fun onStop() {
            handleStop()
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action != ACTION_SET_MEDIA_FILES || extras == null) return
            val uris = extras.getStringArrayList(EXTRA_URIS) ?: return
            val names = extras.getStringArrayList(EXTRA_NAMES) ?: return
            val sizes = extras.getLongArray(EXTRA_SIZES) ?: return

            mediaCacheService.clearCache()
            val count = minOf(uris.size, names.size, sizes.size)
            for (i in 0 until count) {
                mediaCacheService.addFile(
                    MediaFileInfo(
                        uriString = uris[i],
                        displayName = names[i],
                        sizeBytes = sizes[i]
                    )
                )
            }
            notifyChildrenChanged(ROOT_ID)
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
        if (parentId == ROOT_ID) {
            val items = mediaCacheService.cachedFiles.map { fileInfo ->
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(fileInfo.uriString)
                    .setTitle(fileInfo.displayName)
                    .build()
                MediaItem(description, MediaItem.FLAG_PLAYABLE)
            }.toMutableList()
            result.sendResult(items)
        } else {
            result.sendResult(ArrayList())
        }
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
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
    }

    private fun updatePlaybackState(state: Int) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val speed = if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f
        val actions = when (state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_STOP
            }
            else -> {
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            }
        }

        playbackStateBuilder
            .setActions(actions)
            .setState(state, position, speed, SystemClock.elapsedRealtime())
        session.setPlaybackState(playbackStateBuilder.build())
    }

    private fun updateMetadata(fileInfo: MediaFileInfo) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, fileInfo.uriString)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, fileInfo.displayName)
            .build()
        session.setMetadata(metadata)
    }
}
