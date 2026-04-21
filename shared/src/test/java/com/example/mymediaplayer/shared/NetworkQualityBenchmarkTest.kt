package com.example.mymediaplayer.shared

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Smoke benchmark comparing async OkHttp concurrency against the historical
 * `HttpURLConnection` baseline.
 *
 * This test is **ignored in CI** to avoid adding wall-clock time. Run it locally
 * when making network-layer changes.
 *
 * Usage: comment out `@Ignore` and run the test manually.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Ignore("Benchmark — comment out @Ignore to run locally")
class NetworkQualityBenchmarkTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowConnectivityManager: ShadowConnectivityManager
    private lateinit var testClient: OkHttpClient

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowConnectivityManager = shadowOf(connectivityManager)

        shadowConnectivityManager.setDefaultNetworkActive(true)
        val network = connectivityManager.activeNetwork!!
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(connectivityManager).setNetworkCapabilities(network, capabilities)

        // Dedicated test client with no latency — we measure pure async overhead.
        testClient = OkHttpClient.Builder()
            .connectTimeout(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            })
            .build()
    }

    @After
    fun tearDown() {
        testClient.dispatcher.executorService.shutdown()
        testClient.connectionPool.evictAll()
    }

    @Test
    fun benchmark_concurrent_measureQuality() = runBlocking {
        val iterations = 50
        val concurrentRequests = 10

        // Build a shared checker + warm up
        val warmupChecker = NetworkQualityChecker(context, testClient)
        for (i in 0 until 5) {
            warmupChecker.invalidate()
            warmupChecker.check()
        }

        val start = System.currentTimeMillis()
        for (i in 0 until iterations) {
            val deferreds = (0 until concurrentRequests).map {
                async(Dispatchers.Default) {
                    val localChecker = NetworkQualityChecker(context, testClient)
                    localChecker.invalidate()
                    localChecker.check()
                }
            }
            deferreds.awaitAll()
        }
        val elapsed = System.currentTimeMillis() - start
        println("BENCHMARK_RESULT_CONCURRENT: $elapsed ms for $iterations iterations with $concurrentRequests concurrent requests")
    }
}
