package com.example.mymediaplayer.shared

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkQualityCheckerTest {

    private lateinit var context: Context
    private lateinit var checker: NetworkQualityChecker
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowConnectivityManager: ShadowConnectivityManager

    companion object {
        var mockLatencyMs: Long = 0
        var mockFailConnection = false
        var factoryInstalled = false

        fun installMockFactory() {
            if (!factoryInstalled) {
                try {
                    URL.setURLStreamHandlerFactory { protocol ->
                        if (protocol == "https") {
                            object : URLStreamHandler() {
                                override fun openConnection(u: URL): URLConnection {
                                    return object : HttpURLConnection(u) {
                                        override fun connect() {
                                            if (mockFailConnection) throw java.io.IOException("Mock connection failed")
                                            Thread.sleep(mockLatencyMs) // simulate latency
                                        }
                                        override fun disconnect() {}
                                        override fun usingProxy(): Boolean = false
                                    }
                                }
                            }
                        } else {
                            null
                        }
                    }
                    factoryInstalled = true
                } catch (e: Error) {
                    // Factory already set
                }
            }
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        checker = NetworkQualityChecker(context)

        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowConnectivityManager = shadowOf(connectivityManager)

        mockLatencyMs = 0
        mockFailConnection = false

        installMockFactory()

        NetworkQualityChecker.testInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            if (mockFailConnection) throw java.io.IOException("Mock connection failed")
            // Pass the latency specifically so Robolectric can read it deterministically
            okhttp3.Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("X-Mock-Latency", mockLatencyMs.toString())
                .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                .build()
        }
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
