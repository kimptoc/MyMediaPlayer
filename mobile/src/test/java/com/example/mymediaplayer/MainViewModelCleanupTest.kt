package com.example.mymediaplayer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaCacheService
import com.example.mymediaplayer.shared.MediaFileInfo
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelCleanupTest {

    @Test
    fun onCleared_clearsCaches() {
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
                title = "Song One"
            )
        )

        val scanCacheField = viewModel.javaClass.getDeclaredField("scanCache")
        scanCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scanCache = scanCacheField.get(viewModel) as MutableMap<String, String>
        scanCache["key"] = "value"

        val onCleared = viewModel.javaClass.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(viewModel)

        assertTrue(mediaCache.cachedFiles.isEmpty())
        assertTrue(scanCache.isEmpty())
    }
}
