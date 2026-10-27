package com.example.mymediaplayer.shared

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Saves the playback queue's URIs to their own file, off the main thread, and only when the
 * queue itself changes (#518 point 8). They used to be one string in the encrypted prefs,
 * rewritten 3-4 times per track: up to 1.4MB encrypted on the main thread for a 14k-song
 * queue, and every small prefs write rewrote it again.
 *
 * Call [saveIfChanged] and [markWritten] from the main thread. The queue is an immutable list
 * that is replaced whenever it changes, so a reference check tells whether it needs writing.
 */
internal class QueueStore(
    file: File,
    private val executor: ExecutorService = sharedWriter
) {
    private val atomicFile = AtomicFile(file)
    private var lastWritten: List<MediaFileInfo>? = null

    /** Writes started, for tests. */
    var writeCount = 0
        private set

    /** Writes [queue] unless it's the list last written; [afterWrite] runs once it's on disk. */
    fun saveIfChanged(queue: List<MediaFileInfo>, afterWrite: (() -> Unit)? = null) {
        if (queue === lastWritten) return
        val previous = lastWritten
        lastWritten = queue
        // Same songs in the same order, such as titles filled in once the library loads.
        if (previous != null && previous.size == queue.size &&
            previous.indices.all { previous[it].uriString == queue[it].uriString }
        ) return
        writeCount++
        executor.execute { if (write(queue)) afterWrite?.invoke() }
    }

    /** Records [queue] as already on disk, such as one just restored from this file. */
    fun markWritten(queue: List<MediaFileInfo>) {
        lastWritten = queue
    }

    /** The saved URIs, or null if nothing has been saved to the file yet. */
    fun read(): List<String>? {
        // A service instance that just ended may still be writing; read what it saved.
        flush()
        if (!atomicFile.baseFile.exists()) return null
        return runCatching {
            atomicFile.readFully().toString(Charsets.UTF_8).split('\n').filter { it.isNotBlank() }
        }.onFailure { Log.w(TAG, "Failed to read saved queue", it) }.getOrNull()
    }

    /** Waits (briefly) for writes already queued, from any instance. */
    fun flush() {
        runCatching { executor.submit {}.get(FLUSH_WAIT_MS, TimeUnit.MILLISECONDS) }
            .onFailure { Log.w(TAG, "Timed out waiting for queue writes", it) }
    }

    private fun write(queue: List<MediaFileInfo>): Boolean {
        val stream = runCatching { atomicFile.startWrite() }
            .onFailure { Log.w(TAG, "Failed to save queue", it) }
            .getOrNull() ?: return false
        return try {
            stream.write(queue.joinToString("\n") { it.uriString }.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
            true
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            Log.w(TAG, "Failed to save queue", e)
            false
        }
    }

    companion object {
        private const val TAG = "QueueStore"
        private const val FLUSH_WAIT_MS = 2_000L

        // One writer for the process, shared by every service instance, so writes stay in order.
        val sharedWriter: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "QueueStore").apply { isDaemon = true }
        }
    }
}
