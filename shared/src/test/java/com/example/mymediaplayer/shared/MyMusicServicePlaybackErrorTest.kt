package com.example.mymediaplayer.shared

import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServicePlaybackErrorTest {

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
    }

    // When the consecutive-error budget is exhausted, Android Auto must see STATE_ERROR
    // so it shows a failure message rather than spinning forever.
    @Test
    fun handlePlaybackError_whenBudgetExhausted_emitsStateError() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        setConsecutiveErrors(service, 2) // one more call makes it 3 = MAX, triggering giveup

        service.handlePlaybackError("all retries exhausted")

        val state = MediaControllerCompat(service, service.sessionToken!!).playbackState?.state
        assertEquals(PlaybackStateCompat.STATE_ERROR, state)
    }

    // When there's nothing left in the queue to advance to, the same STATE_ERROR must persist —
    // handleStop() must not overwrite it with STATE_STOPPED.
    @Test
    fun handlePlaybackError_whenQueueExhausted_emitsStateError() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        // consecutivePlaybackErrors = 0 (default), playlistQueue = empty (default)
        // → budget not exhausted, but advanceQueueOnError() returns false

        service.handlePlaybackError("no tracks left in queue")

        val state = MediaControllerCompat(service, service.sessionToken!!).playbackState?.state
        assertEquals(PlaybackStateCompat.STATE_ERROR, state)
    }

    // Regression: when queue can advance, the advance path is taken (not the error-state path).
    @Test
    fun handlePlaybackError_whenQueueAdvances_advancesQueueIndex() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        service.mediaCacheService.addFile(
            MediaFileInfo(uriString = "content://test/1", displayName = "a.mp3", sizeBytes = 1L)
        )
        service.mediaCacheService.addFile(
            MediaFileInfo(uriString = "content://test/2", displayName = "b.mp3", sizeBytes = 1L)
        )
        setPlaylistQueue(service, service.mediaCacheService.cachedFiles)
        setQueueIndex(service, 0)
        // queue has 2 tracks at index 0; advanceQueueOnError() should move to index 1

        service.handlePlaybackError("transient error, queue can advance")

        // The queue index advanced, proving the advance branch was taken (not error-state path)
        val queueIndex = getQueueIndex(service)
        assertEquals(true, queueIndex >= 1)
    }

    private fun setConsecutiveErrors(service: MyMusicService, count: Int) {
        val field = MyMusicService::class.java.getDeclaredField("consecutivePlaybackErrors")
        field.isAccessible = true
        field.set(service, count)
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

    private fun getQueueIndex(service: MyMusicService): Int {
        val field = MyMusicService::class.java.getDeclaredField("currentQueueIndex")
        field.isAccessible = true
        return field.getInt(service)
    }
}
