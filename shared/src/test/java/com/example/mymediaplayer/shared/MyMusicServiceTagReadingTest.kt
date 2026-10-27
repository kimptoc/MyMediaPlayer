package com.example.mymediaplayer.shared

import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlin.coroutines.CoroutineContext

/**
 * Each song's tags were read on the main thread as it started, and the next song's too when
 * announcements were on (#518 point 8). They're now read through
 * [MyMusicService.metadataDispatcher] while the player prepares, and the song starts once both
 * are done.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceTagReadingTest {

    /** Holds tag reads until the test runs them. */
    private class HeldDispatcher : CoroutineDispatcher() {
        val held = mutableListOf<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(held) { held += block }
        }
        fun runAll() {
            val blocks = synchronized(held) { held.toList().also { held.clear() } }
            blocks.forEach { it.run() }
        }
    }

    private lateinit var service: MyMusicService
    private val players = mutableListOf<Pair<MediaPlayer, ShadowMediaPlayer>>()
    private val tagReads = HeldDispatcher()

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
        MyMusicService.metadataDispatcher = Dispatchers.IO
        ShadowMediaPlayer.setCreateListener(null)
        NetworkQualityChecker.testInterceptor = null
    }

    @Test
    fun aSongWaitsForItsTagsBeforeStarting_andTheyAreNotReadOnTheMainThread() {
        createService()
        play()

        assertFalse("prepared, but its tags haven't been read yet", players.last().first.isPlaying)

        tagReads.runAll()
        idle()

        assertTrue(players.last().first.isPlaying)
        assertEquals(songs[0].uriString, service.currentMediaIdForTesting())
    }

    @Test
    fun tagsReadForASkippedSongDontStartIt() {
        createService()
        play()

        service.handleSkipToNextForTesting()
        idle()
        tagReads.runAll()
        idle()

        assertFalse("the first song's player was released", players.first().first.isPlaying)
        assertTrue(players.last().first.isPlaying)
        assertEquals(songs[1].uriString, service.currentMediaIdForTesting())
    }

    private fun createService() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        MyMusicService.metadataDispatcher = tagReads
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
