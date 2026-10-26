package com.example.mymediaplayer.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Each generated announcement is a request against the cloud voice's quota, so only the
 * announcements that are switched on are generated, and the outro and next intro wait until
 * the track is nearly over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnnouncementPreGeneratorSchedulingTest {

    private val current = track("current")
    private val next = track("next")

    @Test
    fun trackStart_withIntrosOff_generatesNothing() = runTest {
        val generator = generator(this.testScheduler)

        generator.prepareTrackStart(current, introEnabled = false)

        assertEquals(emptySet<Pair<String, Boolean>>(), generator.scheduledForTesting())
    }

    @Test
    fun trackStart_withIntrosOn_generatesOnlyItsOwnIntro() = runTest {
        val generator = generator(this.testScheduler)

        generator.prepareTrackStart(current, introEnabled = true)

        assertEquals(setOf("current" to true), generator.scheduledForTesting())
    }

    @Test
    fun trackEnd_withOnlyOutrosOn_generatesOnlyTheOutro() = runTest {
        val generator = generator(this.testScheduler)

        generator.prepareTrackEnd(current, next, introEnabled = false, outroEnabled = true)

        assertEquals(setOf("current" to false), generator.scheduledForTesting())
    }

    @Test
    fun trackEnd_withBothOn_generatesOutroAndNextIntro_whichTheNextStartKeeps() = runTest {
        val generator = generator(this.testScheduler)

        generator.prepareTrackEnd(current, next, introEnabled = true, outroEnabled = true)
        assertEquals(setOf("current" to false, "next" to true), generator.scheduledForTesting())

        generator.prepareTrackStart(next, introEnabled = true)
        assertEquals(setOf("next" to true), generator.scheduledForTesting())
    }

    private fun generator(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        AnnouncementPreGenerator(ApplicationProvider.getApplicationContext<Context>(), TestScope(scheduler))

    private fun track(uri: String) =
        MediaFileInfo(uriString = uri, displayName = "$uri.mp3", sizeBytes = 1L, title = uri, artist = "Artist")
}
