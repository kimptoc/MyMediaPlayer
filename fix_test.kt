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
import kotlin.coroutines.resume

@RunWith(RobolectricTestRunner::class)
class TempTest {

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

        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
             kotlin.coroutines.suspendCoroutine<Any?> { cont ->
                 val direct = extractMethod.invoke(service, context, candidate, cont)
                 if (direct != kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                     cont.resume(direct)
                 }
             }
        } as MediaFileInfo?

        println("Result: $result")
    }
}
