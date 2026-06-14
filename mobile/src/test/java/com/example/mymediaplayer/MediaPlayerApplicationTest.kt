package com.example.mymediaplayer

import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = MediaPlayerApplication::class)
class MediaPlayerApplicationTest {

    @Before
    fun setUp() {
        Timber.uprootAll()
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `onCreate initializes Timber and does not crash`() {
        val app = ApplicationProvider.getApplicationContext<MediaPlayerApplication>()
        assertNotNull(app)

        app.onCreate()

        val expectedTreeCount = if (BuildConfig.DEBUG) 1 else 0
        assertEquals(expectedTreeCount, Timber.treeCount)
    }
}
