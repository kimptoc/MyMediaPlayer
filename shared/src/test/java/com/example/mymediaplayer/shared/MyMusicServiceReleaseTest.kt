package com.example.mymediaplayer.shared

import android.media.MediaPlayer
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaPlayer

@Implements(MediaPlayer::class)
class ShadowMediaPlayerThrowingStop : ShadowMediaPlayer() {
    companion object {
        var stopCalled = false
        var releaseCalled = false
    }

    @Implementation
    fun stop() {
        stopCalled = true
        throw IllegalStateException("Simulated stop exception")
    }

    @Implementation
    fun release() {
        releaseCalled = true
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowMediaPlayerThrowingStop::class])
class MyMusicServiceReleaseTest {

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        ShadowMediaPlayerThrowingStop.stopCalled = false
        ShadowMediaPlayerThrowingStop.releaseCalled = false
    }

    @Test
    fun releaseMediaPlayer_whenStopThrowsIllegalStateException_isCaughtAndReleaseIsCalled() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        val player = MediaPlayer()

        // Set the private mediaPlayer field on the service using reflection
        val field = MyMusicService::class.java.getDeclaredField("mediaPlayer")
        field.isAccessible = true
        field.set(service, player)

        // Invoke the private releaseMediaPlayer method using reflection
        val method = MyMusicService::class.java.getDeclaredMethod("releaseMediaPlayer")
        method.isAccessible = true
        method.invoke(service)

        // Verify that stop() was called and threw the exception, but release() was still called and mediaPlayer was set to null
        assertTrue("stop() should have been called", ShadowMediaPlayerThrowingStop.stopCalled)
        assertTrue("release() should have been called despite stop() throwing", ShadowMediaPlayerThrowingStop.releaseCalled)
        assertNull("mediaPlayer field should have been set to null", field.get(service))
    }
}
