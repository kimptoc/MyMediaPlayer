package com.example.mymediaplayer.shared

import android.net.Uri
import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
 * Issue #517: pressing Play after the queue has played to the end used to replay only the
 * last song. A plain queue should start again from its first song, and a shuffled one should
 * start a fresh shuffle of the same songs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceQueueEndTest {

    private lateinit var service: MyMusicService

    private val songs = (1..5).map {
        MediaFileInfo(
            uriString = "content://test/song$it",
            displayName = "song$it.mp3",
            sizeBytes = 1L,
            title = "Song $it"
        )
    }

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        songs.forEach { service.mediaCacheService.addFile(it) }
        registerPlayableSongs(service)
    }

    @Test
    fun playAfterPlainQueueEnds_restartsFromFirstSong() {
        playUiList(shuffle = false)
        val queue = service.currentPlaylistQueue()
        finishQueue()

        service.handlePlayForTesting()
        idle()

        assertEquals(queue, service.currentPlaylistQueue())
        assertEquals(0, service.currentQueueIndexForTesting())
        assertEquals(queue[0].uriString, service.currentMediaIdForTesting())
    }

    @Test
    fun playAfterShuffledQueueEnds_startsFreshShuffleOfSameSongs() {
        playUiList(shuffle = true)
        val finishedQueue = service.currentPlaylistQueue()
        val lastSong = finishedQueue.last()
        finishQueue()

        service.handlePlayForTesting()
        idle()

        val newQueue = service.currentPlaylistQueue()
        assertEquals(finishedQueue.toSet(), newQueue.toSet())
        assertEquals(finishedQueue.size, newQueue.size)
        assertEquals(0, service.currentQueueIndexForTesting())
        assertNotEquals("new pass must not open with the song that just ended", lastSong, newQueue[0])
        assertEquals(newQueue[0].uriString, service.currentMediaIdForTesting())
    }

    @Test
    fun playAfterStoppingMidQueue_resumesCurrentSong() {
        playUiList(shuffle = false)
        service.setQueueIndexForTesting(2)
        service.handleStopForTesting()
        idle()

        service.handlePlayForTesting()
        idle()

        assertEquals(2, service.currentQueueIndexForTesting())
        assertEquals(songs[2].uriString, service.currentMediaIdForTesting())
    }

    @Test
    fun finishedShuffledQueue_isRememberedAcrossRestart() = runBlocking {
        playUiList(shuffle = true)
        finishQueue()

        MyMusicService.clearPrefsCacheForTesting()
        val restarted = Robolectric.buildService(MyMusicService::class.java).create().get()
        songs.forEach { restarted.mediaCacheService.addFile(it) }
        registerPlayableSongs(restarted)
        restarted.restorePlaybackSnapshot()
        val lastSong = restarted.currentPlaylistQueue().last()

        restarted.handlePlayForTesting()
        idle()

        assertEquals(0, restarted.currentQueueIndexForTesting())
        assertEquals(songs.toSet(), restarted.currentPlaylistQueue().toSet())
        assertNotEquals(lastSong, restarted.currentPlaylistQueue()[0])
    }

    /** Puts the service where it ends up when the last song completes with repeat off. */
    private fun finishQueue() {
        service.setQueueIndexForTesting(service.currentPlaylistQueue().lastIndex)
        service.onTrackCompletedForTesting()
        idle()
    }

    /** The service path behind the phone's Play/Shuffle buttons (ACTION_PLAY_UI_LIST). */
    private fun playUiList(shuffle: Boolean) {
        val method = MyMusicService::class.java.getDeclaredMethod(
            "playProvidedUriList",
            List::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(service, songs.map { it.uriString }, shuffle, "Test list", false)
        idle()
    }

    private fun registerPlayableSongs(target: MyMusicService) {
        songs.forEach {
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(target, Uri.parse(it.uriString)),
                ShadowMediaPlayer.MediaInfo(1000, 0)
            )
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
