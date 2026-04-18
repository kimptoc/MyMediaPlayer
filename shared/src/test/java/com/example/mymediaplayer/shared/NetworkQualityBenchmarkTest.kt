package com.example.mymediaplayer.shared

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkQualityBenchmarkTest {

    private lateinit var context: Context
    private lateinit var checker: NetworkQualityChecker
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowConnectivityManager: ShadowConnectivityManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        checker = NetworkQualityChecker(context)

        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowConnectivityManager = shadowOf(connectivityManager)

        shadowConnectivityManager.setDefaultNetworkActive(true)
        val network = connectivityManager.activeNetwork!!
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(connectivityManager).setNetworkCapabilities(network, capabilities)
        checker.invalidate()

        NetworkQualityCheckerTest.installMockFactory()
        NetworkQualityCheckerTest.mockLatencyMs = 5L // Use small latency to speed up local tests
        NetworkQualityCheckerTest.mockFailConnection = false

        NetworkQualityChecker.testInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            if (NetworkQualityCheckerTest.mockFailConnection) throw java.io.IOException("Mock connection failed")
            // No sleep here to benchmark actual async overhead vs blocking overhead
            okhttp3.Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(okhttp3.ResponseBody.create(null, ""))
                .build()
        }
    }

    @Test
    fun benchmark_concurrent_measureQuality() = runBlocking {
        val iterations = 50
        val concurrentRequests = 10

        // Warm up
        for (i in 0 until 5) {
            checker.invalidate()
            checker.check()
        }

        val start = System.currentTimeMillis()
        for (i in 0 until iterations) {
            val deferreds = (0 until concurrentRequests).map {
                async(Dispatchers.Default) {
                    val localChecker = NetworkQualityChecker(context)
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
