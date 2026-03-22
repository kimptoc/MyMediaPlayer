package com.example.mymediaplayer.shared

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.GeneralSecurityException

@RunWith(RobolectricTestRunner::class)
class ApiKeyStoreTest {

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
}
