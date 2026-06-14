package com.example.mymediaplayer

import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = MediaPlayerApplication::class)
class MediaPlayerApplicationTest {

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `onCreate initializes Timber and does not crash`() {
        // Robolectric instantiates the Application and calls onCreate() as part of
        // ApplicationProvider.getApplicationContext(), so no manual onCreate() call here.
        val app = ApplicationProvider.getApplicationContext<MediaPlayerApplication>()
        assertNotNull(app)

        val expectedTreeCount = if (BuildConfig.DEBUG) 1 else 0
        assertEquals(expectedTreeCount, Timber.treeCount)
    }
}
