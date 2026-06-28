package com.example.mymediaplayer.shared

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for issue #375: shuffle queue must not be overwritten when
 * onPlayFromMediaId fires with the currently-playing track's URI (e.g. on
 * Bluetooth reconnect some head units send onPlayFromMediaId instead of onPlay).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceShuffleQueueTest {

    private lateinit var service: MyMusicService

    private val song1 = MediaFileInfo(uriString = "content://test/song1", displayName = "song1.mp3", sizeBytes = 1L, title = "Song One")
    private val song2 = MediaFileInfo(uriString = "content://test/song2", displayName = "song2.mp3", sizeBytes = 1L, title = "Song Two")
    private val song3 = MediaFileInfo(uriString = "content://test/song3", displayName = "song3.mp3", sizeBytes = 1L, title = "Song Three")

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        service.mediaCacheService.addFile(song1)
        service.mediaCacheService.addFile(song2)
        service.mediaCacheService.addFile(song3)
    }

    /**
     * Guard condition helper: verifies that the preservation guard evaluates to true
     * when the current track in the shuffled queue is the one being requested.
     */
    @Test
    fun queuePreservationGuard_firesWhenCurrentTrackRequested() {
        val shuffledQueue = listOf(song2, song3, song1)
        setPlaylistQueue(shuffledQueue)
        setQueueIndex(1) // pointing to song3

        assertEquals(true, service.queuePreservationGuardWouldFire(song3.uriString))
    }

    @Test
    fun queuePreservationGuard_doesNotFireWhenDifferentTrackRequested() {
        val shuffledQueue = listOf(song2, song3, song1)
        setPlaylistQueue(shuffledQueue)
        setQueueIndex(1) // pointing to song3

        assertEquals(false, service.queuePreservationGuardWouldFire(song1.uriString))
    }

    @Test
    fun queuePreservationGuard_doesNotFireWhenQueueEmpty() {
        setPlaylistQueue(emptyList())
        setQueueIndex(-1)

        assertEquals(false, service.queuePreservationGuardWouldFire(song1.uriString))
    }

    /**
     * Integration: when the currently-playing track is requested via handlePlaySingleItem,
     * the shuffled queue order must be preserved (not rebuilt sequentially from browse context).
     *
     * Note: playTrack() may trigger error recovery in the Robolectric test environment
     * (content:// URIs are unresolvable), which can advance currentQueueIndex. The key
     * assertion is that the QUEUE LIST stays in shuffled order — the fix guards against
     * rebuilding it sequentially from lastBrowseParentId.
     */
    @Test
    fun handlePlaySingleItem_preservesShuffledQueueOrderWhenCurrentTrackRequested() = runBlocking {
        val shuffledQueue = listOf(song2, song3, song1)
        setPlaylistQueue(shuffledQueue)
        setQueueIndex(1) // pointing to song3
        setLastBrowseParentId(MyMusicService.SONGS_ID) // would give sequential order if guard didn't fire

        service.handlePlaySingleItemForTesting(song3.uriString)

        // The queue list must still be in shuffled order, not rebuilt as [song1, song2, song3]
        val queue = service.currentPlaylistQueue()
        assertEquals(3, queue.size)
        assertEquals(song2.uriString, queue[0].uriString)
        assertEquals(song3.uriString, queue[1].uriString)
        assertEquals(song1.uriString, queue[2].uriString)
    }

    /**
     * Integration: when a DIFFERENT track is requested via handlePlaySingleItem,
     * the queue IS rebuilt from the browse context (sequential order).
     */
    @Test
    fun handlePlaySingleItem_rebuildsQueueWhenDifferentTrackRequested() = runBlocking {
        val shuffledQueue = listOf(song2, song3, song1)
        setPlaylistQueue(shuffledQueue)
        setQueueIndex(1) // pointing to song3
        setLastBrowseParentId(MyMusicService.SONGS_ID) // sequential: [song1, song2, song3]

        // User explicitly picks song1 from the browser (different from current song3)
        service.handlePlaySingleItemForTesting(song1.uriString)

        // Queue should be rebuilt from the browse context (sequential order)
        val queue = service.currentPlaylistQueue()
        assertEquals(3, queue.size)
        // Sequential order from SONGS_ID: song1, song2, song3
        assertEquals(song1.uriString, queue[0].uriString)
        assertEquals(song2.uriString, queue[1].uriString)
        assertEquals(song3.uriString, queue[2].uriString)
    }

    private fun setPlaylistQueue(tracks: List<MediaFileInfo>) {
        val field = MyMusicService::class.java.getDeclaredField("playlistQueue")
        field.isAccessible = true
        field.set(service, tracks)
    }

    private fun setQueueIndex(index: Int) {
        val field = MyMusicService::class.java.getDeclaredField("currentQueueIndex")
        field.isAccessible = true
        field.setInt(service, index)
    }

    private fun setLastBrowseParentId(parentId: String) {
        val field = MyMusicService::class.java.getDeclaredField("lastBrowseParentId")
        field.isAccessible = true
        field.set(service, parentId)
    }
}
