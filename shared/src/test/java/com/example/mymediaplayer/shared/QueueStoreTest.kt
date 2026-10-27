package com.example.mymediaplayer.shared

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QueueStoreTest {

    private val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "queue_test.txt")
    private val songs = (1..3).map {
        MediaFileInfo(uriString = "content://test/song$it", displayName = "song$it.mp3", sizeBytes = 1L, title = "Song $it")
    }

    @Test
    fun readsBackWhatWasSaved() {
        val store = QueueStore(file)
        assertNull("nothing saved yet", store.read())

        store.saveIfChanged(songs)

        assertEquals(songs.map { it.uriString }, store.read())
    }

    @Test
    fun theSameSongsWithNewTitlesAreNotWrittenAgain() {
        val store = QueueStore(file)
        store.saveIfChanged(songs)

        // As when titles are filled in once the library loads.
        store.saveIfChanged(songs.map { it.copy(title = "${it.title} (tagged)") })

        assertEquals(1, store.writeCount)
    }

    @Test
    fun aNewOrderIsWritten() {
        val store = QueueStore(file)
        store.saveIfChanged(songs)

        store.saveIfChanged(songs.reversed())

        assertEquals(2, store.writeCount)
        assertEquals(songs.reversed().map { it.uriString }, store.read())
    }

    @Test
    fun afterWriteRunsOnceTheQueueIsOnDisk() {
        val store = QueueStore(file)
        var savedWhenCalled: List<String>? = null

        store.saveIfChanged(songs) { savedWhenCalled = file.readText().split('\n') }
        store.flush()

        assertEquals(songs.map { it.uriString }, savedWhenCalled)
    }

    @Test
    fun aFailedWriteIsTriedAgainWithTheSameQueue() {
        // A plain file where the queue's folder should be makes the write fail.
        val blocker = File(file.parentFile, "blocked").apply { writeText("") }
        val store = QueueStore(File(blocker, "queue.txt"))
        var written = false

        store.saveIfChanged(songs) { written = true }
        store.flush()
        assertEquals(false, written)

        blocker.delete()
        store.saveIfChanged(songs) { written = true }
        store.flush()

        assertEquals(true, written)
        assertEquals(songs.map { it.uriString }, store.read())
    }
}
