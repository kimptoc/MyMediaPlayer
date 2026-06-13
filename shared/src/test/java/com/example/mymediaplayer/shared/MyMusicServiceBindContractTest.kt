package com.example.mymediaplayer.shared

import android.os.Process
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    companion object {
        private const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"
    }

    /**
     * Regression: MyMusicService must NOT declare a `android:permission` attribute.
     *
     * gearhead (com.google.android.projection.gearhead) cannot hold
     * BIND_MEDIA_BROWSER_SERVICE because it is a signature-level permission.
     * Declaring it on the service element silently prevents the bind. See PR #243.
     *
     * Parses the manifest directly rather than going through Robolectric's
     * PackageManager — resolving MyMusicService as a component there requires
     * `includeAndroidResources` and a matching `@Config(sdk = ...)`/targetSdk,
     * which conflicts with the sdk levels used by other tests in this module.
     */
    @Test
    fun myMusicService_doesNotRequireBindPermission() {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val manifest = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))

        val serviceElements = manifest.getElementsByTagName("service")
        val myMusicService = (0 until serviceElements.length)
            .map { serviceElements.item(it) as Element }
            .first { it.getAttributeNS(androidNamespace, "name") == MyMusicService::class.java.name }

        assertFalse(
            "MyMusicService must not declare a permission requirement — gearhead can't hold " +
                "signature-level perms like BIND_MEDIA_BROWSER_SERVICE. See PR #243.",
            myMusicService.hasAttributeNS(androidNamespace, "permission")
        )
    }

    /**
     * Regression: MyMusicService must call startForeground() when the Android Auto package
     * connects so Samsung's "Freecess" freezer cannot freeze the process before the user's
     * first play command. Without foreground promotion the AA binder transaction fails with
     * error -32 ("sent binder to frozen apps"). See PR #243.
     */
    @Test
    fun myMusicService_isPromotedToForegroundOnGetRoot() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()

        // Simulate the Android Auto (gearhead) package connecting via MediaBrowser.
        // onGetRoot() is where the foreground promotion is triggered.
        service.onGetRoot(GEARHEAD_PACKAGE, Process.myUid(), null)

        val shadow = shadowOf(service)
        assertNotNull(
            "MyMusicService must call startForeground() when onGetRoot() is called by the " +
                "gearhead package — Samsung Freecess freezes the service between sessions and " +
                "AA's first binder transaction fails. See PR #243.",
            shadow.lastForegroundNotification
        )
    }
}
