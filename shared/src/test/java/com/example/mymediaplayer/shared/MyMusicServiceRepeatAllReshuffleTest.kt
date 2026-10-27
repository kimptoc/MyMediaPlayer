package com.example.mymediaplayer.shared

import android.net.Uri
import android.os.Looper
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

/**
 * Issue #518 (point 3): with Repeat All on, a shuffled queue used to replay the same order on
 * every pass. Each wrap back to the start should begin a fresh shuffle of the same songs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceRepeatAllReshuffleTest {

    private lateinit var service: MyMusicService

    // Enough songs that a fresh shuffle repeating the old order is vanishingly unlikely (1 in 12!).
    private val songs = (1..12).map {
        MediaFileInfo(
            uriString = "content://test/song$it",
            displayName = "song$it.mp3",
            sizeBytes = 1L,
            title = "Song $it"
        )
    }

    @After
    fun resetTagReading() {
        MyMusicService.metadataDispatcher = Dispatchers.IO
    }

    @Before
    fun setup() {
        // Reads each song's tags inline, so it starts as soon as the main looper is idled.
        MyMusicService.metadataDispatcher = Dispatchers.Unconfined
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        songs.forEach {
            service.mediaCacheService.addFile(it)
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(service, Uri.parse(it.uriString)),
                ShadowMediaPlayer.MediaInfo(1000, 0)
            )
        }
    }

    @Test
    fun shuffledQueue_wrapsAfterLastSongEnds_withFreshShuffle() {
        val pass = startShuffledRepeatAll()

        service.onTrackCompletedForTesting()
        idle()

        assertFreshPass(pass)
    }

    @Test
    fun shuffledQueue_wrapsOnNextFromLastSong_withFreshShuffle() {
        val pass = startShuffledRepeatAll()

        service.handleSkipToNextForTesting()
        idle()

        assertFreshPass(pass)
    }

    @Test
    fun shuffledQueue_wrapsWhenLastSongFails_withFreshShuffle() {
        val pass = startShuffledRepeatAll()

        service.handlePlaybackError("broken file")
        idle()

        assertFreshPass(pass)
    }

    @Test
    fun shuffledQueue_eachPassIsAFreshShuffle() {
        startShuffledRepeatAll()
        val passes = mutableListOf(service.currentPlaylistQueue())

        repeat(5) {
            service.setQueueIndexForTesting(service.currentPlaylistQueue().lastIndex)
            service.onTrackCompletedForTesting()
            idle()
            passes += service.currentPlaylistQueue()
        }

        passes.zipWithNext().forEach { (previous, next) ->
            assertFalse("a pass must not just rotate the last one", isRotation(previous, next))
            assertFalse("a pass must not just reverse the last one", next == previous.reversed())
        }
        assertTrue(
            "passes should open with different songs",
            passes.map { it.first() }.toSet().size > 1
        )
    }

    @Test
    fun shuffledQueue_onLastSong_doesNotGuessNextSong() {
        startShuffledRepeatAll()

        assertNull("the next pass isn't shuffled yet", service.peekNextQueueTrackForTesting())
    }

    @Test
    fun plainQueue_onLastSong_peeksFirstSong() {
        play(shuffle = false)
        service.setRepeatModeForTesting(PlaybackStateCompat.REPEAT_MODE_ALL)
        service.setQueueIndexForTesting(songs.lastIndex)

        assertEquals(songs[0].uriString, service.peekNextQueueTrackForTesting()?.uriString)
    }

    @Test
    fun plainQueue_wrapsInSameOrder() {
        play(shuffle = false)
        service.setRepeatModeForTesting(PlaybackStateCompat.REPEAT_MODE_ALL)
        val pass = service.currentPlaylistQueue()
        service.setQueueIndexForTesting(pass.lastIndex)

        service.onTrackCompletedForTesting()
        idle()

        assertEquals(pass, service.currentPlaylistQueue())
        assertEquals(0, service.currentQueueIndexForTesting())
    }

    @Test
    fun shuffledQueue_previousFromFirstSong_keepsOrder() {
        play(shuffle = true)
        service.setRepeatModeForTesting(PlaybackStateCompat.REPEAT_MODE_ALL)
        val pass = service.currentPlaylistQueue()

        service.handleSkipToPreviousForTesting()
        idle()

        assertEquals("going back shouldn't reshuffle", pass, service.currentPlaylistQueue())
        assertEquals(pass.lastIndex, service.currentQueueIndexForTesting())
    }

    /** A shuffled, Repeat All queue sitting on its last song. Returns the pass that is ending. */
    private fun startShuffledRepeatAll(): List<MediaFileInfo> {
        play(shuffle = true)
        service.setRepeatModeForTesting(PlaybackStateCompat.REPEAT_MODE_ALL)
        val pass = service.currentPlaylistQueue()
        service.setQueueIndexForTesting(pass.lastIndex)
        return pass
    }

    private fun assertFreshPass(previous: List<MediaFileInfo>) {
        val next = service.currentPlaylistQueue()
        assertEquals(previous.toSet(), next.toSet())
        assertEquals(previous.size, next.size)
        assertNotEquals("Repeat All should reshuffle, not replay the same order", previous, next)
        assertNotEquals("the new pass mustn't open with the song that just ended", previous.last(), next[0])
        assertEquals(0, service.currentQueueIndexForTesting())
        assertEquals(next[0].uriString, service.currentMediaIdForTesting())
    }

    private fun isRotation(a: List<MediaFileInfo>, b: List<MediaFileInfo>): Boolean =
        (a.indices).any { shift -> a.drop(shift) + a.take(shift) == b }

    private fun play(shuffle: Boolean) {
        service.playProvidedUriListForTesting(songs.map { it.uriString }, shuffle, "Test list")
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
