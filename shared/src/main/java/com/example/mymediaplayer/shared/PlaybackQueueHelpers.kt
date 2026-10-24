package com.example.mymediaplayer.shared

import kotlin.random.Random

/**
 * Index to skip to after the track at [currentIndex] fails to play, or null to give up.
 * With [repeatAll] a failure on the last track wraps to the start, matching normal
 * track completion. A single-track queue never wraps, as that would just retry the
 * track that failed.
 */
internal fun nextQueueIndexForError(
    currentIndex: Int,
    queueSize: Int,
    repeatAll: Boolean = false
): Int? {
    if (queueSize <= 0 || currentIndex < 0) return null
    val nextIndex = currentIndex + 1
    return when {
        nextIndex < queueSize -> nextIndex
        repeatAll && queueSize > 1 -> 0
        else -> null
    }
}

internal fun shouldRetryPlaybackError(consecutiveErrors: Int, maxErrors: Int): Boolean {
    return consecutiveErrors < maxErrors
}

/**
 * A new shuffled order of [queue] for another pass. The song that just ended ([lastPlayed])
 * is kept off the front, so the new pass doesn't open by repeating it.
 */
internal fun <T> reshuffleForNewPass(queue: List<T>, lastPlayed: T?, random: Random = Random.Default): List<T> {
    val shuffled = queue.shuffled(random).toMutableList()
    if (shuffled.size > 1 && shuffled[0] == lastPlayed) {
        val swapWith = 1 + random.nextInt(shuffled.size - 1)
        shuffled[0] = shuffled[swapWith].also { shuffled[swapWith] = shuffled[0] }
    }
    return shuffled
}
