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

@RunWith(RobolectricTestRunner::class)
class MediaMetadataHelperTest {

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
}
