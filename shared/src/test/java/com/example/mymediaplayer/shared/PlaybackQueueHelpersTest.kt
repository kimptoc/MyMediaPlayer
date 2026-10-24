package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackQueueHelpersTest {

    @Test
    fun nextQueueIndexForError_returnsNextWhenAvailable() {
        assertEquals(2, nextQueueIndexForError(currentIndex = 1, queueSize = 3))
    }

    @Test
    fun nextQueueIndexForError_returnsNullWhenNoNext() {
        assertEquals(null, nextQueueIndexForError(currentIndex = 2, queueSize = 3))
    }

    @Test
    fun nextQueueIndexForError_returnsNullWhenQueueEmpty() {
        assertEquals(null, nextQueueIndexForError(currentIndex = 0, queueSize = 0))
    }

    @Test
    fun nextQueueIndexForError_returnsNullWhenCurrentIndexNegative() {
        assertEquals(null, nextQueueIndexForError(currentIndex = -1, queueSize = 3))
    }

    @Test
    fun nextQueueIndexForError_wrapsToStartWhenRepeatAll() {
        assertEquals(0, nextQueueIndexForError(currentIndex = 2, queueSize = 3, repeatAll = true))
    }

    @Test
    fun nextQueueIndexForError_repeatAllStillAdvancesMidQueue() {
        assertEquals(2, nextQueueIndexForError(currentIndex = 1, queueSize = 3, repeatAll = true))
    }

    @Test
    fun nextQueueIndexForError_repeatAllDoesNotRetrySingleTrack() {
        assertEquals(null, nextQueueIndexForError(currentIndex = 0, queueSize = 1, repeatAll = true))
    }

    @Test
    fun shouldRetryPlaybackError_respectsLimit() {
        assertTrue(shouldRetryPlaybackError(consecutiveErrors = 0, maxErrors = 3))
        assertTrue(shouldRetryPlaybackError(consecutiveErrors = 2, maxErrors = 3))
        assertFalse(shouldRetryPlaybackError(consecutiveErrors = 3, maxErrors = 3))
        assertFalse(shouldRetryPlaybackError(consecutiveErrors = 4, maxErrors = 3))
    }

    @Test
    fun reshuffleForNewPass_keepsSameSongsAndNeverOpensWithLastPlayed() {
        val queue = (1..5).toList()
        repeat(200) { seed ->
            val reshuffled = reshuffleForNewPass(queue, lastPlayed = 5, random = Random(seed))
            assertEquals(queue.sorted(), reshuffled.sorted())
            assertTrue("seed $seed opened with the last song", reshuffled[0] != 5)
        }
    }

    @Test
    fun reshuffleForNewPass_singleSongQueue() {
        assertEquals(listOf(7), reshuffleForNewPass(listOf(7), lastPlayed = 7))
        assertEquals(emptyList<Int>(), reshuffleForNewPass(emptyList(), lastPlayed = null))
    }
}
