package com.example.mymediaplayer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaCacheService
import com.example.mymediaplayer.shared.MediaFileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTrimMemoryTest {

    @Test
    fun trimMemory_clearsScanCacheAndIndexesButKeepsCachedFiles() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        val mediaCacheField = viewModel.javaClass.getDeclaredField("mediaCacheService")
        mediaCacheField.isAccessible = true
        val mediaCache = mediaCacheField.get(viewModel) as MediaCacheService
        mediaCache.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "Song One",
                sizeBytes = 1L,
                title = "Song One",
                album = "Album"
            )
        )
        mediaCache.buildAlbumArtistIndexesFromCache()

        val scanCacheField = viewModel.javaClass.getDeclaredField("scanCache")
        scanCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scanCache = scanCacheField.get(viewModel) as MutableMap<String, String>
        scanCache["key"] = "value"

        assertTrue(mediaCache.hasAlbumArtistIndexes())

        viewModel.trimMemory()

        assertFalse(mediaCache.hasAlbumArtistIndexes())
        assertTrue(scanCache.isEmpty())
        assertEquals(1, mediaCache.cachedFiles.size)
    }
}
