package com.example.mymediaplayer.shared

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.time.Duration

/**
 * Issue #518: a refused or delayed audio-focus request at a track change must leave the
 * track paused and waiting (retrying, or resuming on AUDIOFOCUS_GAIN), not stop playback
 * with STATE_ERROR.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceAudioFocusTest {

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
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(service, Uri.parse(song1.uriString)),
            ShadowMediaPlayer.MediaInfo(1000, 0)
        )
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(service, Uri.parse(song2.uriString)),
            ShadowMediaPlayer.MediaInfo(1000, 0)
        )
        setField("playlistQueue", listOf(song1, song2))
        setField("currentQueueIndex", 1)
    }

    @Test
    fun refusedFocus_leavesTrackPausedAndRetries() {
        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        getCallback().onPlay()
        idle()

        assertTrue(service.isAwaitingAudioFocus())
        assertEquals(PlaybackStateCompat.STATE_PAUSED, playbackState())
        assertEquals(song2.uriString, (getField("currentFileInfo") as MediaFileInfo?)?.uriString)
        assertNull(getField("mediaPlayer"))

        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        idleFor(Duration.ofSeconds(3))

        assertFalse(service.isAwaitingAudioFocus())
        assertTrue((getField("mediaPlayer") as MediaPlayer).isPlaying)
    }

    @Test
    fun refusedFocus_stopsRetryingAfterLimit_butPlayStillResumes() {
        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        getCallback().onPlay()
        idle()
        idleFor(Duration.ofSeconds(60))

        assertFalse(service.isAwaitingAudioFocus())
        assertEquals(PlaybackStateCompat.STATE_PAUSED, playbackState())

        // Retries have stopped: focus becoming available doesn't start anything by itself.
        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        idleFor(Duration.ofSeconds(30))
        assertNull(getField("mediaPlayer"))

        getCallback().onPlay()
        idle()

        assertFalse(service.isAwaitingAudioFocus())
        assertTrue((getField("mediaPlayer") as MediaPlayer).isPlaying)
    }

    @Test
    fun delayedFocus_startsTrackOnFocusGain() {
        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)

        getCallback().onPlay()
        idle()

        assertTrue(service.isAwaitingAudioFocus())
        assertEquals(PlaybackStateCompat.STATE_PAUSED, playbackState())

        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        getFocusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        idle()

        assertFalse(service.isAwaitingAudioFocus())
        assertTrue((getField("mediaPlayer") as MediaPlayer).isPlaying)
    }

    @Test
    fun pauseWhileWaiting_cancelsRetry() {
        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        getCallback().onPlay()
        idle()

        getCallback().onPause()
        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        idleFor(Duration.ofSeconds(3))

        assertFalse(service.isAwaitingAudioFocus())
        assertNull(getField("mediaPlayer"))
    }

    @Test
    fun pauseWhileResumeAwaitsDelayedFocus_abandonsRequest() {
        val player = MediaPlayer()
        player.setDataSource(service, Uri.parse(song1.uriString))
        player.prepare()
        setField("mediaPlayer", player)
        setFocusResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)

        getCallback().onPlay()
        assertFalse(player.isPlaying)

        getCallback().onPause()

        assertNotNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
        assertNull(getField("audioFocusRequest"))
    }

    private fun setFocusResponse(response: Int) {
        shadowOf(audioManager).setNextFocusRequestResponse(response)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun idleFor(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration)

    private fun playbackState(): Int? =
        MediaControllerCompat(service, service.sessionToken!!).playbackState?.state

    private fun getCallback(): MediaSessionCompat.Callback =
        getField("callback") as MediaSessionCompat.Callback

    private fun getFocusListener(): AudioManager.OnAudioFocusChangeListener =
        getField("audioFocusChangeListener") as AudioManager.OnAudioFocusChangeListener

    private fun setField(name: String, value: Any?) {
        val field = MyMusicService::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(service, value)
    }

    private fun getField(name: String): Any? {
        val field = MyMusicService::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(service)
    }
}
