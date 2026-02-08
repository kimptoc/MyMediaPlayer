package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCacheServiceTest {

    @Test
    fun scanDirectory_reportsProgressEvenWhenEmpty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = DocumentsContract.buildTreeDocumentUri("test", "root")
        val service = MediaCacheService()
        val progress = mutableListOf<Pair<Int, Int>>()

        service.scanDirectory(context, treeUri, maxFiles = 10) { songsFound, foldersScanned ->
            progress += songsFound to foldersScanned
        }

        assertTrue(service.cachedFiles.size <= 10)
        assertTrue(progress.isNotEmpty())
        val last = progress.last()
        assertEquals(service.cachedFiles.size, last.first)
        assertTrue(last.second >= 0)
    }

    @Test
    fun scanDirectory_canBeCancelled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = DocumentsContract.buildTreeDocumentUri("test", "root")
        val service = MediaCacheService()

        val job: Job = launch {
            service.scanDirectory(context, treeUri, maxFiles = 10) { _, _ -> }
        }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
    }
}
