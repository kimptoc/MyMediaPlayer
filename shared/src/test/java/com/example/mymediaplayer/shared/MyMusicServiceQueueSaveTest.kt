package com.example.mymediaplayer.shared

import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * The whole queue was saved into the encrypted prefs, and resent to the session, 3-4 times on
 * every track change (#518 point 8). For a 14k-song queue that's 1.4MB encrypted on the main
 * thread per save. The queue now goes to its own file, and to the session, only when it changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceQueueSaveTest {

    private lateinit var service: MyMusicService
    private val players = mutableListOf<Pair<MediaPlayer, ShadowMediaPlayer>>()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // Enough songs that a reshuffle repeating the old order is vanishingly unlikely (1 in 12!).
    private val songs = (1..12).map {
        MediaFileInfo(uriString = "content://test/song$it", displayName = "song$it.mp3", sizeBytes = 1L, title = "Song $it")
    }
    private val uris = songs.map { it.uriString }

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        // Keeps announcement generation off the real network.
        NetworkQualityChecker.testInterceptor = okhttp3.Interceptor { throw java.io.IOException("offline") }
        prefs().edit()
            .putBoolean("track_voice_intro_enabled", false)
            .putBoolean("track_voice_outro_enabled", false)
            .commit()
        ShadowMediaPlayer.setCreateListener { player, shadow -> players += player to shadow }
    }

    @After
    fun teardown() {
        ShadowMediaPlayer.setCreateListener(null)
        NetworkQualityChecker.testInterceptor = null
    }

    @Test
    fun movingToTheNextSongNeitherRewritesNorResendsTheQueue() {
        createService()
        play(shuffle = false)
        val writes = service.queueFileWritesForTesting()
        val sessionUpdates = service.sessionQueueUpdatesForTesting()

        completeSong()
        completeSong()

        assertEquals(songs[2].uriString, service.currentMediaIdForTesting())
        assertEquals("queue file writes", writes, service.queueFileWritesForTesting())
        assertEquals("session queue updates", sessionUpdates, service.sessionQueueUpdatesForTesting())
        assertEquals(2, prefs().getInt("resume_queue_index", -1))
    }

    @Test
    fun theQueueIsSavedToItsOwnFileNotThePrefs() {
        createService()
        play(shuffle = false)
        service.flushQueueWritesForTesting()

        assertEquals(uris.joinToString("\n"), queueFile().readText())
        assertFalse(prefs().contains("resume_queue_uris"))
    }

    @Test
    fun aRepeatAllReshuffleSavesTheNewOrder() {
        createService()
        play(shuffle = true)
        service.setRepeatModeForTesting(PlaybackStateCompat.REPEAT_MODE_ALL)
        val firstPass = service.currentPlaylistQueue()
        val writes = service.queueFileWritesForTesting()

        service.setQueueIndexForTesting(songs.lastIndex)
        service.onTrackCompletedForTesting()
        idle()
        service.flushQueueWritesForTesting()

        val secondPass = service.currentPlaylistQueue()
        assertNotEquals("reshuffled", firstPass, secondPass)
        assertEquals(writes + 1, service.queueFileWritesForTesting())
        assertEquals(secondPass.joinToString("\n") { it.uriString }, queueFile().readText())
    }

    @Test
    fun aSavedQueueComesBackInANewService() {
        createService()
        play(shuffle = true)
        completeSong()
        val queue = service.currentPlaylistQueue()
        service.flushQueueWritesForTesting()

        createService()

        assertEquals(queue, service.currentPlaylistQueue())
        assertEquals(1, service.currentQueueIndexForTesting())
        assertEquals("restoring doesn't write the queue straight back", 0, service.queueFileWritesForTesting())
    }

    @Test
    fun aQueueInTheOldPrefsKeyMovesToTheFile() {
        prefs().edit()
            .putString("resume_queue_uris", uris.joinToString("\n"))
            .putInt("resume_queue_index", 3)
            .putString("resume_media_uri", uris[3])
            .commit()

        createService()
        service.flushQueueWritesForTesting()

        assertEquals(uris, service.currentPlaylistQueue().map { it.uriString })
        assertEquals(3, service.currentQueueIndexForTesting())
        assertEquals(uris.joinToString("\n"), queueFile().readText())
        assertFalse("removed once the file holds the queue", prefs().contains("resume_queue_uris"))
    }

    @Test
    fun theSavedSongWinsWhenTheSavedIndexDisagrees() {
        // A crash after the index was saved but before the new queue reached the file.
        queueFile().writeText(uris.joinToString("\n"))
        prefs().edit()
            .putInt("resume_queue_index", 1)
            .putString("resume_media_uri", uris[4])
            .commit()

        createService()

        assertEquals(4, service.currentQueueIndexForTesting())
    }

    private fun createService() {
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        songs.forEach {
            service.mediaCacheService.addFile(it)
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(service, Uri.parse(it.uriString)),
                ShadowMediaPlayer.MediaInfo(100_000, 0)
            )
        }
        // The cache was empty during onCreate, so a saved queue waits for it.
        service.resolvePendingQueueIfNeeded()
    }

    private fun play(shuffle: Boolean) {
        service.playProvidedUriListForTesting(uris, shuffle = shuffle, queueTitle = "Test list")
        idle()
    }

    private fun completeSong() {
        players.last().second.invokeCompletionListener()
        idle()
    }

    private fun prefs() = MyMusicService.getPrefs(context)

    private fun queueFile() = File(context.filesDir, MyMusicService.QUEUE_FILE_NAME)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
