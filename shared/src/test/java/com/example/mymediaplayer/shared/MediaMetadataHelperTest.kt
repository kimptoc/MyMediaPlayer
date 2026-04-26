package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource

import android.media.MediaMetadataRetriever
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(MediaMetadataRetriever::class)
class ShadowMediaMetadataRetrieverThrowingRelease : ShadowMediaMetadataRetriever() {
    companion object {
        var releaseCalled = false
    }

    @Implementation
    fun release() {
        releaseCalled = true
        throw RuntimeException("Simulated release exception")
    }
}

@RunWith(RobolectricTestRunner::class)
class MediaMetadataHelperTest {

    @Test
    @Config(shadows = [ShadowMediaMetadataRetrieverThrowingRelease::class])
    fun extractMetadata_whenReleaseThrowsException_isCaughtAndDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uriString = "content://something/valid"
        ShadowMediaMetadataRetrieverThrowingRelease.releaseCalled = false

        // This will call extractMetadata, which will create MediaMetadataRetriever and eventually call release()
        MediaMetadataHelper.extractMetadata(context, uriString)

        // Verify that the mocked release function was actually called
        org.junit.Assert.assertTrue("release() should have been called", ShadowMediaMetadataRetrieverThrowingRelease.releaseCalled)
    }

    @Test
    fun extractMetadata_withInvalidUri_returnsEmptyMediaMetadataInfo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val invalidUriString = "this-is-not-a-valid-uri"

        val result = MediaMetadataHelper.extractMetadata(context, invalidUriString)

        // In Robolectric, an unconfigured DataSource does not throw an exception by default.
        // It returns null for all metadata keys.
        assertNull(result?.title)
        assertNull(result?.album)
        assertNull(result?.artist)
        assertNull(result?.genre)
        assertNull(result?.year)
        assertNull(result?.durationMs)
    }

    @Test
    fun extractMetadata_whenRetrieverThrowsException_returnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uriString = "content://something/error"
        val uri = Uri.parse(uriString)

        // Configure ShadowMediaMetadataRetriever to throw an exception for this specific DataSource
        val dataSource = DataSource.toDataSource(context, uri)
        ShadowMediaMetadataRetriever.addException(dataSource, IllegalArgumentException("Simulated failure"))

        val result = MediaMetadataHelper.extractMetadata(context, uriString)

        // The exception caught block should return null
        assertNull("Extracting metadata should return null if an exception is thrown", result)
    }

    @Test
    fun extractMetadata_withEmptyMetadata_logsWarningAndReturnsEmptyInfo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uriString = "content://media/empty"

        val result = MediaMetadataHelper.extractMetadata(context, uriString)

        assertNull(result?.title)
        assertNull(result?.artist)
        assertNull(result?.album)
        assertNull(result?.genre)
        assertNull(result?.year)
        assertNull(result?.durationMs)

        val logs = org.robolectric.shadows.ShadowLog.getLogsForTag("MediaMetadataHelper")
        logs.forEach { println("LOG: ${it.type} ${it.msg}") }
        val warningLog = logs.find { it.type == android.util.Log.WARN && it.msg == "No ID3 tags found for: $uriString" }
        org.junit.Assert.assertNotNull("Expected warning log for missing ID3 tags was not found", warningLog)
    }
}
