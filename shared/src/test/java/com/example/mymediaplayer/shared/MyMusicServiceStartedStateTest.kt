package com.example.mymediaplayer.shared

import android.content.ComponentName
import android.net.Uri
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * Issue #516: the phone app only binds to the service, so when rotation recreates the activity
 * and it unbinds, a service that was never started is destroyed and playback stops. Playing must
 * put the service in the started state, and stopping must release it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceStartedStateTest {

    private lateinit var service: MyMusicService

    private val songs = (1..3).map {
        MediaFileInfo(
            uriString = "content://test/song$it",
            displayName = "song$it.mp3",
            sizeBytes = 1L,
            title = "Song $it"
        )
    }

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        songs.forEach {
            service.mediaCacheService.addFile(it)
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(service, Uri.parse(it.uriString)),
                ShadowMediaPlayer.MediaInfo(1000, 0)
            )
        }
    }

    @Test
    fun playing_startsTheService() {
        service.playProvidedUriListForTesting(songs.map { it.uriString }, shuffle = false, queueTitle = "Test list")
        idle()

        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertNotNull("playing must start the service so unbinding doesn't destroy it", started)
        assertEquals(ComponentName(service, MyMusicService::class.java), started.component)
        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun stopping_releasesTheStartedService() {
        service.playProvidedUriListForTesting(songs.map { it.uriString }, shuffle = false, queueTitle = "Test list")
        idle()

        service.handleStopForTesting()
        idle()

        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
