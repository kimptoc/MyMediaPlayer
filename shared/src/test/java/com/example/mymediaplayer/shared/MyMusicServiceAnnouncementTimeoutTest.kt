package com.example.mymediaplayer.shared

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
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

        ShadowLog.clear()
        finish()
        finish()
        idleFor(Duration.ofSeconds(30))

        assertEquals(1, completions)
        assertFalse(timeoutLogged())
    }

    @Test
    fun abandonedAnnouncement_doesNotComplete() {
        var completions = 0
        service.guardAnnouncementCompletion { completions++ }

        // A new track starting abandons the announcement via clearPendingIntro().
        val clear = MyMusicService::class.java.getDeclaredMethod("clearPendingIntro")
        clear.isAccessible = true
        ShadowLog.clear()
        clear.invoke(service)
        idleFor(Duration.ofSeconds(30))

        assertEquals(0, completions)
        assertFalse(timeoutLogged())
    }

    @Test
    fun skipPendingAnnouncement_completesImmediately() {
        var completions = 0
        service.guardAnnouncementCompletion { completions++ }

        service.skipPendingAnnouncement()
        idleFor(Duration.ofSeconds(30))

        assertEquals(1, completions)
    }

    @Test
    fun replacedAnnouncementFinishing_doesNotCancelNewerTimeout() {
        var oldCompletions = 0
        var newCompletions = 0
        val oldFinish = service.guardAnnouncementCompletion { oldCompletions++ }
        service.guardAnnouncementCompletion { newCompletions++ }

        oldFinish()
        idleFor(Duration.ofSeconds(21))

        assertEquals(1, oldCompletions)
        assertEquals(1, newCompletions)
    }

    private fun timeoutLogged(): Boolean =
        ShadowLog.getLogsForTag("MyMusicService").any { it.msg.contains("did not finish within") }

    private fun idleFor(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration)
}
