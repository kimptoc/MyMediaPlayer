package com.example.mymediaplayer

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `onCreate initializes Timber and does not crash`() {
        val app = ApplicationProvider.getApplicationContext<MediaPlayerApplication>()
        assertNotNull(app)

        app.onCreate()

        assertTrue("Timber should have planted at least one tree", Timber.treeCount > 0)
    }
}
