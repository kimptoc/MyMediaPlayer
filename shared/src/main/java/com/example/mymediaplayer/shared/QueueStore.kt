package com.example.mymediaplayer.shared

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Saves the playback queue's URIs to their own file, off the main thread, and only when the
 * queue itself changes (#518 point 8). They used to be one string in the encrypted prefs,
 * rewritten 3-4 times per track: up to 1.4MB encrypted on the main thread for a 14k-song
 * queue, and every small prefs write rewrote it again.
 *
 * The queue is an immutable list that is replaced whenever it changes, so a reference check
 * tells whether it needs writing. Safe to call from any thread: the session queue is also
 * refreshed from the IO dispatcher once the library loads.
 */
internal class QueueStore(
    file: File,
    private val executor: ExecutorService = sharedWriter
) {
    private val atomicFile = AtomicFile(file)
    // Guarded by this. Reset if a write fails, so the same queue is tried again next time.
    private var lastWritten: List<MediaFileInfo>? = null

    /** Writes started (not skipped as unchanged), for tests. */
    var writeCount = 0
        private set

    /** Writes [queue] unless it's the list last written; [afterWrite] runs once it's on disk. */
    fun saveIfChanged(queue: List<MediaFileInfo>, afterWrite: (() -> Unit)? = null) {
        synchronized(this) {
            if (queue === lastWritten) return
            val previous = lastWritten
            lastWritten = queue
            // Same songs in the same order, such as titles filled in once the library loads.
            if (previous != null && previous.size == queue.size &&
                previous.indices.all { previous[it].uriString == queue[it].uriString }
            ) return
            writeCount++
        }
        // Counted and queued together, so flush() never sees the count without the write queued.
        synchronized(writerLock) {
            pendingWrites.incrementAndGet()
            executor.execute {
                try {
                    if (write(queue)) {
                        afterWrite?.invoke()
                    } else {
                        synchronized(this) { if (lastWritten === queue) lastWritten = null }
                    }
                } finally {
                    pendingWrites.decrementAndGet()
                }
            }
        }
    }

    /** Records [queue] as already on disk, such as one just restored from this file. */
    fun markWritten(queue: List<MediaFileInfo>) {
        synchronized(this) { lastWritten = queue }
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

    /** Waits (briefly) for writes already queued, from any instance; returns at once if none are. */
    fun flush() {
        // The writer runs tasks in order, so this marker finishes after every write queued so far.
        val marker = synchronized(writerLock) {
            if (pendingWrites.get() == 0) null else executor.submit {}
        } ?: return
        runCatching { marker.get(FLUSH_WAIT_MS, TimeUnit.MILLISECONDS) }
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

        // Writes queued on sharedWriter and not yet finished, across all instances. Incremented
        // and queued under writerLock.
        private val pendingWrites = AtomicInteger()
        private val writerLock = Any()

        // One writer for the process, shared by every service instance, so writes stay in order.
        val sharedWriter: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "QueueStore").apply { isDaemon = true }
        }
    }
}
