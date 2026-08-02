package com.example.mymediaplayer.shared

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.GeneralSecurityException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
class ApiKeyStoreTest {

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
    }

    @After
    fun teardown() {
        ApiKeyStore.testInterceptor = null
    }

    @Test
    fun getPrefs_success_returnsSharedPreferences() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val result = ApiKeyStore.getPrefs(baseContext)
        assertNotNull("getPrefs should return a SharedPreferences instance on success", result)
    }

    @Test
    fun getPrefs_whenExceptionThrown_returnsNull() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        // Create a custom ContextWrapper that throws an exception when
        // SharedPreferences are requested, simulating a failure in
        // EncryptedSharedPreferences or Android Keystore.
        EncryptedPrefsManager.clearCacheForTesting()
        val failingContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context {
                return this
            }

            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                throw GeneralSecurityException("Simulated Keystore failure")
            }
        }

        val result = ApiKeyStore.getPrefs(failingContext)

        assertNull("getPrefs should return null when an exception occurs", result)
    }

    @Test
    fun validateKeys_whenPrefsNull_returnsErrorPair() = runBlocking {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        // Simulate failure in getting preferences
        EncryptedPrefsManager.clearCacheForTesting()
        val failingContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context {
                return this
            }

            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                throw GeneralSecurityException("Simulated Keystore failure")
            }
        }

        val result = ApiKeyStore.validateKeys(failingContext)

        val expectedError = ApiKeyStore.ValidationResult.Error("Encrypted storage unavailable")
        assertEquals(Pair(expectedError, expectedError), result)
    }

    @Test
    fun validateKeys_withSuccessfulResponse_returnsSuccessResult() = runBlocking {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        // Store fake API keys in encrypted SharedPreferences
        val prefs = ApiKeyStore.getPrefs(baseContext)!!
        prefs.edit()
            .putString(ApiKeyStore.KEY_KILO, "fake_kilo_key")
            .putString(ApiKeyStore.KEY_CLOUD_TTS, "fake_tts_key")
            .commit()

        // Set up test interceptor to return 200 OK
        ApiKeyStore.testInterceptor = okhttp3.Interceptor { chain ->
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        // Intercepted validation
        val result = ApiKeyStore.validateKeys(baseContext)

        val expectedSuccess = ApiKeyStore.ValidationResult.Success
        assertEquals(Pair(expectedSuccess, expectedSuccess), result)
    }

    @Test
    fun validateKeys_withFailedResponse_returnsErrorResult() = runBlocking {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val prefs = ApiKeyStore.getPrefs(baseContext)!!
        prefs.edit()
            .putString(ApiKeyStore.KEY_KILO, "fake_kilo_key")
            .putString(ApiKeyStore.KEY_CLOUD_TTS, "fake_tts_key")
            .commit()

        // Set up test interceptor to return 403 Forbidden
        ApiKeyStore.testInterceptor = okhttp3.Interceptor { chain ->
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val result = ApiKeyStore.validateKeys(baseContext)

        val expectedErrorKilo = ApiKeyStore.ValidationResult.Error("HTTP 403: API request failed")
        val expectedErrorTts = ApiKeyStore.ValidationResult.Error("HTTP 403: API request failed")
        assertEquals(Pair(expectedErrorKilo, expectedErrorTts), result)
    }

    @Test
    fun validateKeys_withNetworkFailure_returnsErrorResult() = runBlocking {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val prefs = ApiKeyStore.getPrefs(baseContext)!!
        prefs.edit()
            .putString(ApiKeyStore.KEY_KILO, "fake_kilo_key")
            .putString(ApiKeyStore.KEY_CLOUD_TTS, "fake_tts_key")
            .commit()

        // Set up test interceptor to throw java.io.IOException
        ApiKeyStore.testInterceptor = okhttp3.Interceptor {
            throw java.io.IOException("No internet connection")
        }

        val result = ApiKeyStore.validateKeys(baseContext)

        val expectedErrorKilo = ApiKeyStore.ValidationResult.Error("Connection failed: No internet connection")
        val expectedErrorTts = ApiKeyStore.ValidationResult.Error("Connection failed: No internet connection")
        assertEquals(Pair(expectedErrorKilo, expectedErrorTts), result)
    }
}
