package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun nextQueueIndexForError_returnsNullWhenQueueSizeNegative() {
        assertEquals(null, nextQueueIndexForError(currentIndex = 0, queueSize = -1))
    }

    @Test
    fun nextQueueIndexForError_returnsNullWhenCurrentIndexNegative() {
        assertEquals(null, nextQueueIndexForError(currentIndex = -1, queueSize = 3))
    }

    @Test
    fun shouldRetryPlaybackError_returnsTrueWhenConsecutiveErrorsLessThanMax() {
        assertTrue(shouldRetryPlaybackError(consecutiveErrors = 0, maxErrors = 3))
        assertTrue(shouldRetryPlaybackError(consecutiveErrors = 2, maxErrors = 3))
    }

    @Test
    fun shouldRetryPlaybackError_returnsFalseWhenConsecutiveErrorsEqualsMax() {
        assertFalse(shouldRetryPlaybackError(consecutiveErrors = 3, maxErrors = 3))
    }

    @Test
    fun shouldRetryPlaybackError_returnsFalseWhenConsecutiveErrorsGreaterThanMax() {
        assertFalse(shouldRetryPlaybackError(consecutiveErrors = 4, maxErrors = 3))
    }
}
