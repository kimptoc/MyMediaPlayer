package com.example.mymediaplayer.shared

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Skipping through songs used to generate an intro and an outro for every song skipped past,
 * which used up the Gemini voice's quota. The outro and the next intro now wait until
 * [MyMusicService.PRE_GENERATE_BEFORE_END_MS] before the song ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceAnnouncementSchedulingTest {

    private lateinit var service: MyMusicService

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
        NetworkQualityChecker.testInterceptor = null
    }

    @Test
    fun outroOnly_waitsUntilNearTheEndOfTheSong() {
        createService(intro = false, outro = true)
        play()

        assertEquals("nothing is needed at the start", emptySet<Pair<String, Boolean>>(), scheduled())

        idleFor(SONG_MS - MyMusicService.PRE_GENERATE_BEFORE_END_MS - 1_000)
        assertEquals(emptySet<Pair<String, Boolean>>(), scheduled())

        idleFor(2_000)
        assertEquals(setOf(songs[0].uriString to false), scheduled())
    }

    @Test
    fun skippingThroughSongs_withOutroOnly_generatesNothing() {
        createService(intro = false, outro = true)
        play()

        repeat(2) {
            service.handleSkipToNextForTesting()
            idle()
        }

        assertEquals(emptySet<Pair<String, Boolean>>(), scheduled())
    }

    @Test
    fun skippingThroughSongs_withIntros_generatesOnlyTheCurrentIntro() {
        createService(intro = true, outro = true)
        play()

        service.handleSkipToNextForTesting()
        idle()

        assertEquals(setOf(songs[1].uriString to true), scheduled())
    }

    private fun createService(intro: Boolean, outro: Boolean) {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        // Keeps generation off the real network: a failed ping counts as a poor connection.
        NetworkQualityChecker.testInterceptor = okhttp3.Interceptor { throw java.io.IOException("offline") }
        MyMusicService.getPrefs(ApplicationProvider.getApplicationContext())
            .edit()
            .putBoolean("track_voice_intro_enabled", intro)
            .putBoolean("track_voice_outro_enabled", outro)
            .commit()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        songs.forEach {
            service.mediaCacheService.addFile(it)
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(service, Uri.parse(it.uriString)),
                ShadowMediaPlayer.MediaInfo(SONG_MS.toInt(), 0)
            )
        }
    }

    private fun play() {
        service.playProvidedUriListForTesting(songs.map { it.uriString }, shuffle = false, queueTitle = "Test list")
        idle()
    }

    private fun scheduled() = service.scheduledAnnouncementsForTesting()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private companion object {
        const val SONG_MS = 100_000L
    }
}
