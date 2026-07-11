package com.example.mymediaplayer.shared

import kotlinx.coroutines.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.io.OutputStreamWriter
import org.json.JSONObject
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
class ApiKeyStoreOkHttpPerfTest {

    // Dummy method that mimics the old block
    private suspend fun simulateOldValidate(apiKey: String): ApiKeyStore.ValidationResult = withContext(Dispatchers.IO) {
        runCatching {
            // Just simulate blocking for 100ms to mimic network IO
            Thread.sleep(100)
            ApiKeyStore.ValidationResult.Success
        }.getOrElse { e ->
            ApiKeyStore.ValidationResult.Error("Connection failed: ${e.message}")
        }
    }

    // Dummy method that mimics OkHttp async non-blocking
    private suspend fun simulateNewValidate(apiKey: String): ApiKeyStore.ValidationResult = suspendCancellableCoroutine { cont ->
        // Simulate network delay using coroutines (non-blocking)
        // In real okhttp, it enqueue's and uses its own thread pool, but doesn't block caller thread.
        // We'll simulate okhttp's background thread
        Thread {
            Thread.sleep(100)
            cont.resumeWith(Result.success(ApiKeyStore.ValidationResult.Success))
        }.start()
    }

    @Test
    fun benchmarkValidateKeys() = runBlocking {
        // Measure 100 concurrent requests with the old blocking way
        val oldTime = measureTimeMillis {
            val jobs = (1..100).map {
                async { simulateOldValidate("key") }
            }
            jobs.awaitAll()
        }

        val newTime = measureTimeMillis {
            val jobs = (1..100).map {
                async { simulateNewValidate("key") }
            }
            jobs.awaitAll()
        }

        println("Old Time (100 reqs): $oldTime ms")
        println("New Time (100 reqs): $newTime ms")
    }
}
