#!/bin/bash
cat << 'EOF2' > fix_test.kt
package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
class TempTest {
    @Test
    fun extractCandidateMetadata_whenRetrieverThrowsException_andRequiresProbe_returnsNull() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uriString = "content://something/error_probe"
        val uri = Uri.parse(uriString)

        val dataSource = org.robolectric.shadows.util.DataSource.toDataSource(context, uri)
        org.robolectric.shadows.ShadowMediaMetadataRetriever.addException(
            dataSource,
            IllegalArgumentException("Simulated failure")
        )

        val service = MediaCacheService()
        val candidateClass = MediaCacheService::class.java.declaredClasses.find { it.simpleName == "FileCandidate" }!!
        val constructor = candidateClass.declaredConstructors[0]
        constructor.isAccessible = true
        val candidate = constructor.newInstance(uri, "test_probe.mp3", 100L, 1000L, "testFolder", true)

        val extractMethod = MediaCacheService::class.java.getDeclaredMethod(
            "extractCandidateMetadata",
            Context::class.java,
            candidateClass,
            Continuation::class.java
        )
        extractMethod.isAccessible = true

        var suspendResult: Result<Any?>? = null
        val directResult = extractMethod.invoke(service, context, candidate, Continuation<MediaFileInfo?>(EmptyCoroutineContext) {
            suspendResult = it
        })

        val finalResult = if (directResult == kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
            suspendResult?.getOrNull()
        } else {
            directResult
        } as MediaFileInfo?

        assertNull("Expected null when requiresProbe is true and metadata extraction fails", finalResult)
    }

    @Test
    fun extractCandidateMetadata_whenRetrieverThrowsException_andDoesNotRequireProbe_returnsFallback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uriString = "content://something/error_no_probe"
        val uri = Uri.parse(uriString)

        val dataSource = org.robolectric.shadows.util.DataSource.toDataSource(context, uri)
        org.robolectric.shadows.ShadowMediaMetadataRetriever.addException(
            dataSource,
            IllegalArgumentException("Simulated failure")
        )

        val service = MediaCacheService()
        val candidateClass = MediaCacheService::class.java.declaredClasses.find { it.simpleName == "FileCandidate" }!!
        val constructor = candidateClass.declaredConstructors[0]
        constructor.isAccessible = true
        val candidate = constructor.newInstance(uri, "test_fallback.mp3", 100L, 1000L, "testFolder", false)

        val extractMethod = MediaCacheService::class.java.getDeclaredMethod(
            "extractCandidateMetadata",
            Context::class.java,
            candidateClass,
            Continuation::class.java
        )
        extractMethod.isAccessible = true

        var suspendResult: Result<Any?>? = null
        val directResult = extractMethod.invoke(service, context, candidate, Continuation<MediaFileInfo?>(EmptyCoroutineContext) {
            suspendResult = it
        })

        val finalResult = if (directResult == kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
            suspendResult?.getOrNull()
        } else {
            directResult
        } as MediaFileInfo?

        assertNotNull("Expected fallback metadata when requiresProbe is false", finalResult)
        assertEquals("test_fallback", finalResult?.title)
        assertEquals("testFolder", finalResult?.album)
    }
}
EOF2
cp fix_test.kt shared/src/test/java/com/example/mymediaplayer/shared/TempTest.kt
./gradlew :shared:testDebugUnitTest --tests com.example.mymediaplayer.shared.TempTest
