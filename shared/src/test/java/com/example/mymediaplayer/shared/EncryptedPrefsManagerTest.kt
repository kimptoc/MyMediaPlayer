package com.example.mymediaplayer.shared

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], shadows = [EncryptedPrefsManagerTest.ShadowEncryptedSharedPreferences::class, EncryptedPrefsManagerTest.ShadowMasterKeyBuilder::class])
class EncryptedPrefsManagerTest {

    @Implements(androidx.security.crypto.EncryptedSharedPreferences::class)
    class ShadowEncryptedSharedPreferences {
        companion object {
            @Implementation
            @JvmStatic
            fun create(
                context: Context,
                fileName: String,
                masterKey: androidx.security.crypto.MasterKey,
                prefKeyEncryptionScheme: androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme,
                prefValueEncryptionScheme: androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
            ): SharedPreferences {
                return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            }
        }
    }

    @Implements(androidx.security.crypto.MasterKey.Builder::class)
    class ShadowMasterKeyBuilder {
        @org.robolectric.annotation.RealObject
        private lateinit var realObject: androidx.security.crypto.MasterKey.Builder

        @Implementation
        fun __constructor__(context: Context) {
        }

        @Implementation
        fun setKeyScheme(keyScheme: androidx.security.crypto.MasterKey.KeyScheme): androidx.security.crypto.MasterKey.Builder {
             return realObject
        }

        @Implementation
        fun build(): androidx.security.crypto.MasterKey {
            val cls = androidx.security.crypto.MasterKey::class.java
            val constructor = cls.declaredConstructors.firstOrNull { it.parameterTypes.size == 3 }
                 ?: cls.declaredConstructors.first()
            constructor.isAccessible = true

            val params = constructor.parameterTypes
            val constructorArgs = params.map { type ->
                when (type) {
                    String::class.java -> "test_alias"
                    Context::class.java -> ApplicationProvider.getApplicationContext<Context>()
                    else -> null // KeyGenParameterSpec
                }
            }.toTypedArray()

            return constructor.newInstance(*constructorArgs) as androidx.security.crypto.MasterKey
        }
    }

    @Before
    fun setup() {
        // Use reflection to clear the cache instead to ensure test isolation
        // without relying on clearCacheForTesting.
        val field = EncryptedPrefsManager::class.java.getDeclaredField("prefsInstances")
        field.isAccessible = true
        val map = field.get(EncryptedPrefsManager) as java.util.concurrent.ConcurrentHashMap<*, *>
        map.clear()
    }

    @Test
    fun testCreateOrGet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = EncryptedPrefsManager.createOrGet(context, "test_prefs")
        assertNotNull(prefs)

        val prefs2 = EncryptedPrefsManager.createOrGet(context, "test_prefs")
        assertSame(prefs, prefs2)
    }
}
