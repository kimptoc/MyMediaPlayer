package com.example.mymediaplayer.shared

import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * With the screen off, nothing kept the CPU awake between songs: from one song ending, through
 * the outro and intro, to the next song starting. The device could doze there and playback
 * stalled (#518 point 6). A wake lock now covers that gap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceWakeLockTest {

    private lateinit var service: MyMusicService
    private val players = mutableListOf<Pair<MediaPlayer, ShadowMediaPlayer>>()

    private val songs = (1..3).map {
        MediaFileInfo(
            uriString = "content://test/song$it",
            displayName = "song$it.mp3",
            sizeBytes = 1L,
            title = "Song $it"
        )
    }

    @After
    fun teardown() {
        ShadowMediaPlayer.setCreateListener(null)
        NetworkQualityChecker.testInterceptor = null
    }

    @Test
    fun theWakeLockIsHeldFromTheEndOfOneSongUntilTheNextStarts() {
        createService()
        play()
        assertFalse("a playing song keeps the device awake by itself", service.isGapWakeLockHeldForTesting())

        players.last().second.invokeCompletionListener()

        assertTrue("held while moving to the next song", service.isGapWakeLockHeldForTesting())

        idle()

        assertEquals(songs[1].uriString, service.currentMediaIdForTesting())
        assertFalse("released once the next song plays", service.isGapWakeLockHeldForTesting())
    }

    @Test
    fun stoppingReleasesTheWakeLock() {
        createService()
        play()
        players.last().second.invokeCompletionListener()

        service.handleStopForTesting()

        assertFalse(service.isGapWakeLockHeldForTesting())
    }

    private fun createService() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        // Keeps announcement generation off the real network.
        NetworkQualityChecker.testInterceptor = okhttp3.Interceptor { throw java.io.IOException("offline") }
        MyMusicService.getPrefs(ApplicationProvider.getApplicationContext())
            .edit()
            .putBoolean("track_voice_intro_enabled", false)
            .putBoolean("track_voice_outro_enabled", false)
            .commit()
        ShadowMediaPlayer.setCreateListener { player, shadow -> players += player to shadow }
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        songs.forEach {
            service.mediaCacheService.addFile(it)
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(service, Uri.parse(it.uriString)),
                ShadowMediaPlayer.MediaInfo(100_000, 0)
            )
        }
    }

    private fun play() {
        service.playProvidedUriListForTesting(songs.map { it.uriString }, shuffle = false, queueTitle = "Test list")
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
