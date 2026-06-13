package com.example.mymediaplayer.shared

import android.content.ComponentName
import android.os.Process
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression tests for the Android Auto bind path (issue #247 / PR #243).
 *
 * Two bugs were fixed in PR #243:
 *  1. A `android:permission="BIND_MEDIA_BROWSER_SERVICE"` annotation on the service element
 *     prevented gearhead from binding — gearhead cannot hold signature-level permissions.
 *  2. Samsung's "Freecess" aggressive process freezer was killing the service between AA
 *     sessions; fixed by promoting to foreground as soon as the gearhead package connects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceBindContractTest {

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
    }

    /**
     * Regression: MyMusicService must NOT declare a `android:permission` attribute.
     *
     * gearhead (com.google.android.projection.gearhead) cannot hold
     * BIND_MEDIA_BROWSER_SERVICE because it is a signature-level permission.
     * Declaring it on the service element silently prevents the bind. See PR #243.
     */
    @Test
    fun myMusicService_doesNotRequireBindPermission() {
        val context = RuntimeEnvironment.getApplication()
        val pm = context.packageManager
        val componentName = ComponentName(context, MyMusicService::class.java.name)
        @Suppress("DEPRECATION")
        val serviceInfo = pm.getServiceInfo(componentName, 0)

        assertNull(
            "MyMusicService must not declare a permission requirement — gearhead can't hold " +
                "signature-level perms like BIND_MEDIA_BROWSER_SERVICE. See PR #243.",
            serviceInfo.permission
        )
    }

    /**
     * Regression: MyMusicService must call startForeground() when the Android Auto package
     * connects so Samsung's "Freecess" freezer cannot freeze the process before the user's
     * first play command. Without foreground promotion the AA binder transaction fails with
     * error -32 ("sent binder to frozen apps"). See PR #243.
     */
    @Test
    fun myMusicService_isPromotedToForegroundInOnCreate() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()

        // Simulate the Android Auto (gearhead) package connecting via MediaBrowser.
        // onGetRoot() is where the foreground promotion is triggered.
        service.onGetRoot("com.google.android.projection.gearhead", Process.myUid(), null)

        val shadow = shadowOf(service)
        assertNotNull(
            "MyMusicService must call startForeground() before onCreate returns — Samsung Freecess " +
                "freezes the service between sessions and AA's first binder transaction fails. See PR #243.",
            shadow.lastForegroundNotification
        )
    }
}
