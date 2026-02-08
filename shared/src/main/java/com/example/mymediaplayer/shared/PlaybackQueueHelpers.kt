package com.example.mymediaplayer.shared

internal fun nextQueueIndexForError(currentIndex: Int, queueSize: Int): Int? {
    if (queueSize <= 0 || currentIndex < 0) return null
    val nextIndex = currentIndex + 1
    return if (nextIndex < queueSize) nextIndex else null
}

internal fun shouldRetryPlaybackError(consecutiveErrors: Int, maxErrors: Int): Boolean {
    return consecutiveErrors < maxErrors
}
