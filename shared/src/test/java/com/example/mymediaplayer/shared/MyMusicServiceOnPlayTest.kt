package com.example.mymediaplayer.shared

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceOnPlayTest {

    private lateinit var service: MyMusicService
    private lateinit var audioManager: AudioManager

    private val song1 = MediaFileInfo(
        uriString = "content://test/song1",
        displayName = "song1.mp3",
        sizeBytes = 100L,
        title = "Song One"
    )
    private val song2 = MediaFileInfo(
        uriString = "content://test/song2",
        displayName = "song2.mp3",
        sizeBytes = 200L,
        title = "Song Two"
    )

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()

        audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Grant audio focus requests by default
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        // Configure ShadowMediaPlayer MediaInfo for our test songs
        val song1DataSource = DataSource.toDataSource(service, Uri.parse(song1.uriString))
        val song2DataSource = DataSource.toDataSource(service, Uri.parse(song2.uriString))
        ShadowMediaPlayer.addMediaInfo(song1DataSource, ShadowMediaPlayer.MediaInfo(1000, 0))
        ShadowMediaPlayer.addMediaInfo(song2DataSource, ShadowMediaPlayer.MediaInfo(1000, 0))
    }

    @Test
    fun onPlay_whenPlayerIsNull_andQueueNotEmpty_playsTrackFromQueue() {
        // Arrange
        setPlaylistQueue(service, listOf(song1, song2))
        setQueueIndex(service, 1) // Points to song2
        setMediaPlayer(service, null)

        val callback = getCallback(service)

        // Act
        callback.onPlay()

        // Assert
        val currentTrack = getCurrentFileInfo(service)
        assertNotNull(currentTrack)
        assertEquals(song2.uriString, currentTrack?.uriString)

        val player = getMediaPlayer(service)
        assertNotNull(player)
    }

    @Test
    fun onPlay_whenPlayerIsNull_andQueueEmpty_butCurrentFileInfoExists_playsCurrentFileInfo() {
        // Arrange
        setPlaylistQueue(service, emptyList())
        setQueueIndex(service, -1)
        setCurrentFileInfo(service, song1)
        setMediaPlayer(service, null)

        val callback = getCallback(service)

        // Act
        callback.onPlay()

        // Assert
        val currentTrack = getCurrentFileInfo(service)
        assertNotNull(currentTrack)
        assertEquals(song1.uriString, currentTrack?.uriString)

        val player = getMediaPlayer(service)
        assertNotNull(player)
    }

    @Test
    fun onPlay_whenPlayerIsNull_andNoTracksAvailable_doesNothing() {
        // Arrange
        setPlaylistQueue(service, emptyList())
        setQueueIndex(service, -1)
        setCurrentFileInfo(service, null)
        setMediaPlayer(service, null)

        val callback = getCallback(service)

        // Act
        callback.onPlay()

        // Assert
        val currentTrack = getCurrentFileInfo(service)
        assertNull(currentTrack)

        val player = getMediaPlayer(service)
        assertNull(player)
    }

    @Test
    fun onPlay_whenPlayerIsNotNull_andPaused_startsPlayerAndUpdatesState() {
        // Arrange
        val player = MediaPlayer()
        player.setDataSource(service, Uri.parse(song1.uriString))
        player.prepare()
        setMediaPlayer(service, player)

        val callback = getCallback(service)

        // Act
        callback.onPlay()

        // Assert
        assertTrue(player.isPlaying)

        val state = MediaControllerCompat(service, service.sessionToken!!).playbackState?.state
        assertEquals(PlaybackStateCompat.STATE_PLAYING, state)
    }

    private fun getCallback(service: MyMusicService): MediaSessionCompat.Callback {
        val field = MyMusicService::class.java.getDeclaredField("callback")
        field.isAccessible = true
        return field.get(service) as MediaSessionCompat.Callback
    }

    private fun setPlaylistQueue(service: MyMusicService, tracks: List<MediaFileInfo>) {
        val field = MyMusicService::class.java.getDeclaredField("playlistQueue")
        field.isAccessible = true
        field.set(service, tracks)
    }

    private fun setQueueIndex(service: MyMusicService, index: Int) {
        val field = MyMusicService::class.java.getDeclaredField("currentQueueIndex")
        field.isAccessible = true
        field.setInt(service, index)
    }

    private fun setCurrentFileInfo(service: MyMusicService, fileInfo: MediaFileInfo?) {
        val field = MyMusicService::class.java.getDeclaredField("currentFileInfo")
        field.isAccessible = true
        field.set(service, fileInfo)
    }

    private fun getCurrentFileInfo(service: MyMusicService): MediaFileInfo? {
        val field = MyMusicService::class.java.getDeclaredField("currentFileInfo")
        field.isAccessible = true
        return field.get(service) as MediaFileInfo?
    }

    private fun setMediaPlayer(service: MyMusicService, player: MediaPlayer?) {
        val field = MyMusicService::class.java.getDeclaredField("mediaPlayer")
        field.isAccessible = true
        field.set(service, player)
    }

    private fun getMediaPlayer(service: MyMusicService): MediaPlayer? {
        val field = MyMusicService::class.java.getDeclaredField("mediaPlayer")
        field.isAccessible = true
        return field.get(service) as MediaPlayer?
    }
}
