package com.example.mymediaplayer.shared

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Issue #518: an intro/outro whose TTS engine never reports completion must not stall
 * playback forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceAnnouncementTimeoutTest {

    private lateinit var service: MyMusicService

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
    }

    @Test
    fun completionThatNeverArrives_runsAfterTimeout() {
        var completions = 0
        service.guardAnnouncementCompletion { completions++ }

        idleFor(Duration.ofSeconds(19))
        assertEquals(0, completions)

        idleFor(Duration.ofSeconds(2))
        assertEquals(1, completions)
    }

    @Test
    fun normalCompletion_runsOnce_andCancelsTimeout() {
        var completions = 0
        val finish = service.guardAnnouncementCompletion { completions++ }

        finish()
        finish()
        idleFor(Duration.ofSeconds(30))

        assertEquals(1, completions)
    }

    @Test
    fun abandonedAnnouncement_doesNotComplete() {
        var completions = 0
        service.guardAnnouncementCompletion { completions++ }

        // A new track starting abandons the announcement via clearPendingIntro().
        val clear = MyMusicService::class.java.getDeclaredMethod("clearPendingIntro")
        clear.isAccessible = true
        clear.invoke(service)
        idleFor(Duration.ofSeconds(30))

        assertEquals(0, completions)
    }

    @Test
    fun skipPendingAnnouncement_completesImmediately() {
        var completions = 0
        service.guardAnnouncementCompletion { completions++ }

        service.skipPendingAnnouncement()
        idleFor(Duration.ofSeconds(30))

        assertEquals(1, completions)
    }

    private fun idleFor(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration)
}
