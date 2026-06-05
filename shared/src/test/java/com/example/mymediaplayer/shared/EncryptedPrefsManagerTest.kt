package com.example.mymediaplayer.shared

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [EncryptedPrefsManagerTest.ShadowEncryptedSharedPreferences::class, EncryptedPrefsManagerTest.ShadowMasterKeyBuilder::class])
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
                return context.getSharedPreferences(fileName + java.util.UUID.randomUUID().toString(), Context.MODE_PRIVATE)
            }
        }
    }

    @Implements(androidx.security.crypto.MasterKey.Builder::class)
    class ShadowMasterKeyBuilder {

        @org.robolectric.annotation.RealObject
        lateinit var realObject: androidx.security.crypto.MasterKey.Builder

        @Implementation
        fun setKeyScheme(keyScheme: androidx.security.crypto.MasterKey.KeyScheme): androidx.security.crypto.MasterKey.Builder {
             return realObject
        }

        @Implementation
        fun build(): androidx.security.crypto.MasterKey {
            val cls = androidx.security.crypto.MasterKey::class.java
            val constructor = cls.declaredConstructors.firstOrNull { it.parameterTypes.size == 2 }
                 ?: error("Expected 2-param MasterKey constructor, found: ${cls.declaredConstructors.map { it.parameterTypes.size }}")
            constructor.isAccessible = true

            val params = constructor.parameterTypes
            val constructorArgs = params.map { type ->
                when (type) {
                    String::class.java -> "test_alias"
                    Context::class.java -> ApplicationProvider.getApplicationContext<Context>()
                    else -> KeyGenParameterSpec.Builder("test_alias", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).build()
                }
            }.toTypedArray()

            return constructor.newInstance(*constructorArgs) as androidx.security.crypto.MasterKey
        }
    }

    @Before
    fun setup() {
        val field = EncryptedPrefsManager::class.java.getDeclaredField("prefsInstances")
        field.isAccessible = true
        val map = field.get(EncryptedPrefsManager) as ConcurrentHashMap<*, *>
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

    @Test
    fun testDifferentFilesYieldDifferentInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs1 = EncryptedPrefsManager.createOrGet(context, "test_prefs_1")
        val prefs2 = EncryptedPrefsManager.createOrGet(context, "test_prefs_2")

        assertNotSame(prefs1, prefs2)
    }

    @Test
    fun testClearCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        EncryptedPrefsManager.createOrGet(context, "test_prefs")

        EncryptedPrefsManager.clearCacheForTesting()

        // Verify the internal prefsInstances map is actually cleared
        val field = EncryptedPrefsManager::class.java.getDeclaredField("prefsInstances")
        field.isAccessible = true
        val map = field.get(EncryptedPrefsManager) as ConcurrentHashMap<*, *>

        assertTrue(map.isEmpty())
    }

    @Test
    fun testPutAndGet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = requireNotNull(EncryptedPrefsManager.createOrGet(context, "test_prefs"))

        prefs.edit().putString("key", "value").commit()
        assertEquals("value", prefs.getString("key", null))
    }
}
