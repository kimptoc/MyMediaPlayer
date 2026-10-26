package com.example.mymediaplayer

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Issue #516: recreating the activity on rotation must not redo first-launch work. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityRecreateTest {

    private val voiceSearch = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
        .putExtra(SearchManager.QUERY, "test query")

    @Test
    fun claimLibraryRestore_onlyOncePerViewModel() {
        val viewModel = MainViewModel(RuntimeEnvironment.getApplication())

        assertTrue(viewModel.claimLibraryRestore())
        assertFalse("a rotation keeps the ViewModel and its library", viewModel.claimLibraryRestore())
    }

    @Test
    fun freshActivity_handlesLaunchIntent() {
        val activity = Robolectric.buildActivity(MainActivity::class.java, voiceSearch).create().get()

        assertEquals("test query", pendingQuery(activity))
    }

    @Test
    fun recreatedActivity_doesNotReplayLaunchIntent() {
        // A saved-state bundle is what Android passes when rotation recreates the activity.
        // (ActivityController.recreate() can't be used: it connects the MediaBrowser, which
        // Robolectric can't bind.)
        val recreated = Robolectric.buildActivity(MainActivity::class.java, voiceSearch).create(Bundle()).get()

        assertNull("a rotation must not replay the voice search", pendingQuery(recreated))
    }

    // Same private-field check as MainActivityTest: in a unit test there is no connected
    // controller to observe playFromSearch on.
    private fun pendingQuery(activity: MainActivity): String? {
        val field = MainActivity::class.java.getDeclaredField("pendingVoiceSearchQuery")
        field.isAccessible = true
        return field.get(activity) as? String
    }
}
