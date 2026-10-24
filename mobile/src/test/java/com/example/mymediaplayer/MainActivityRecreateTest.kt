package com.example.mymediaplayer

import android.content.Intent
import android.os.Bundle
import android.app.SearchManager
import com.example.mymediaplayer.shared.MediaFileInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #516: recreating the activity on rotation must not redo first-launch work. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityRecreateTest {

    private val song = MediaFileInfo(
        uriString = "content://test/song1",
        displayName = "song1.mp3",
        sizeBytes = 1L,
        title = "Song 1"
    )

    @Test
    fun needsLibraryRestore_onlyWhenNoLibraryAndNoScan() {
        assertTrue(needsLibraryRestore(ScanState()))
        assertFalse(needsLibraryRestore(ScanState(scannedFiles = listOf(song))))
        assertFalse(needsLibraryRestore(ScanState(isScanning = true)))
    }

    @Test
    fun recreatedActivity_doesNotReplayLaunchIntent() {
        val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
            .putExtra(SearchManager.QUERY, "test query")

        // A saved-state bundle is what Android passes when rotation recreates the activity.
        val recreated = Robolectric.buildActivity(MainActivity::class.java, intent).create(Bundle()).get()

        assertNull("a rotation must not replay the voice search", pendingQuery(recreated))
    }

    private fun pendingQuery(activity: MainActivity): String? {
        val field = MainActivity::class.java.getDeclaredField("pendingVoiceSearchQuery")
        field.isAccessible = true
        return field.get(activity) as? String
    }
}
