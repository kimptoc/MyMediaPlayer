package com.example.mymediaplayer.shared

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkQualityCheckerTest {

    private lateinit var context: Context
    private lateinit var checker: NetworkQualityChecker
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowConnectivityManager: ShadowConnectivityManager
    private lateinit var testClient: OkHttpClient

    companion object {
        var mockLatencyMs: Long = 0
        var mockFailConnection = false
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowConnectivityManager = shadowOf(connectivityManager)

        mockLatencyMs = 0
        mockFailConnection = false

        // Build a dedicated test client with a deterministic mock interceptor.
        // Each test class instance gets its own client, avoiding global state.
        testClient = OkHttpClient.Builder()
            .connectTimeout(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .addInterceptor(Interceptor { chain ->
                if (mockFailConnection) throw IOException("Mock connection failed")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("X-Mock-Latency", mockLatencyMs.toString())
                    .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        checker = NetworkQualityChecker(context, testClient)
    }

    @After
    fun tearDown() {
        testClient.dispatcher.executorService.shutdown()
        testClient.connectionPool.evictAll()
    }

    @Test
    fun check_noActiveNetwork_returnsNone() = runBlocking {
        shadowConnectivityManager.setDefaultNetworkActive(false)
        assertEquals(NetworkQualityChecker.Quality.NONE, checker.check())
    }

    @Test
    fun check_nullNetworkCapabilities_returnsNone() = runBlocking {
        shadowConnectivityManager.setDefaultNetworkActive(true)
        val network = connectivityManager.activeNetwork
        assertEquals(NetworkQualityChecker.Quality.NONE, checker.check())
    }

    @Test
    fun check_noInternetCapability_returnsNone() = runBlocking {
        shadowConnectivityManager.setDefaultNetworkActive(true)
        val network = connectivityManager.activeNetwork!!

        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(connectivityManager).setNetworkCapabilities(network, capabilities)

        assertEquals(NetworkQualityChecker.Quality.NONE, checker.check())
    }

    @Test
    fun check_noValidatedCapability_returnsPoor() = runBlocking {
        shadowConnectivityManager.setDefaultNetworkActive(true)
        val network = connectivityManager.activeNetwork!!

        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(connectivityManager).setNetworkCapabilities(network, capabilities)

        assertEquals(NetworkQualityChecker.Quality.POOR, checker.check())
    }

    @Test
    fun check_returnsCachedResult() = runBlocking {
        shadowConnectivityManager.setDefaultNetworkActive(false)

        val result1 = checker.check()
        assertEquals(NetworkQualityChecker.Quality.NONE, result1)

        shadowConnectivityManager.setDefaultNetworkActive(true)
        val network = connectivityManager.activeNetwork!!
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(connectivityManager).setNetworkCapabilities(network, capabilities)

        val result2 = checker.check()

        assertEquals(NetworkQualityChecker.Quality.NONE, result2)
    }

    @Test
    fun check_afterInvalidate_returnsNewResult() = runBlocking {
        shadowConnectivityManager.setDefaultNetworkActive(false)

        assertEquals(NetworkQualityChecker.Quality.NONE, checker.check())

        checker.invalidate()

        shadowConnectivityManager.setDefaultNetworkActive(true)
        val network = connectivityManager.activeNetwork!!
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(connectivityManager).setNetworkCapabilities(network, capabilities)

        assertEquals(NetworkQualityChecker.Quality.POOR, checker.check())
    }

    @Test
    fun check_latencyUnderThreshold_returnsGood() = runBlocking {
        setupNetworkForPing()
        mockLatencyMs = 50L

        assertEquals(NetworkQualityChecker.Quality.GOOD, checker.check())
    }

    @Test
    fun check_latencyOverThreshold_returnsPoor() = runBlocking {
        setupNetworkForPing()
        mockLatencyMs = 250L

        assertEquals(NetworkQualityChecker.Quality.POOR, checker.check())
    }

    @Test
    fun check_pingFails_returnsPoor() = runBlocking {
        setupNetworkForPing()
        mockFailConnection = true

        assertEquals(NetworkQualityChecker.Quality.POOR, checker.check())
    }

    @Test
    fun invalidate_forcesReevaluation() = runBlocking {
        setupNetworkForPing()
        mockLatencyMs = 50L

        // Initial check populates cache
        assertEquals(NetworkQualityChecker.Quality.GOOD, checker.check())

        // Change the network condition
        mockLatencyMs = 250L

        // Without invalidate, it should return the cached value (GOOD)
        assertEquals(NetworkQualityChecker.Quality.GOOD, checker.check())

        // Now invalidate the cache
        checker.invalidate()

        // After invalidate, it should re-evaluate and return the new value (POOR)
        assertEquals(NetworkQualityChecker.Quality.POOR, checker.check())
    }

    private fun setupNetworkForPing() {
        shadowConnectivityManager.setDefaultNetworkActive(true)
        val network = connectivityManager.activeNetwork!!
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(connectivityManager).setNetworkCapabilities(network, capabilities)
        checker.invalidate()
    }
}
