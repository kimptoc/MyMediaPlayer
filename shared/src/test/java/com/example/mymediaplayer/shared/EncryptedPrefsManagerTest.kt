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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [EncryptedPrefsManagerTest.ShadowEncryptedSharedPreferences::class, EncryptedPrefsManagerTest.ShadowMasterKeyBuilder::class])
class EncryptedPrefsManagerTest {

    @Implements(androidx.security.crypto.EncryptedSharedPreferences::class)
    class ShadowEncryptedSharedPreferences {
        companion object {
            var throwGeneralSecurityException = false
            var throwIOException = false
            var throwException = false

            @Implementation
            @JvmStatic
            fun create(
                context: Context,
                fileName: String,
                masterKey: androidx.security.crypto.MasterKey,
                prefKeyEncryptionScheme: androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme,
                prefValueEncryptionScheme: androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
            ): SharedPreferences {
                if (throwGeneralSecurityException) {
                    throw java.security.GeneralSecurityException("Mocked GeneralSecurityException")
                }
                if (throwIOException) {
                    throw java.io.IOException("Mocked IOException")
                }
                if (throwException) {
                    throw java.lang.Exception("Mocked Exception")
                }
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
        EncryptedPrefsManager.clearCacheForTesting()
        ShadowEncryptedSharedPreferences.throwGeneralSecurityException = false
        ShadowEncryptedSharedPreferences.throwIOException = false
        ShadowEncryptedSharedPreferences.throwException = false
    }

    @Test
    fun testCreateOrGet_throwsException() {
        ShadowEncryptedSharedPreferences.throwException = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = EncryptedPrefsManager.createOrGet(context, "test_prefs_exception_fail")
        org.junit.Assert.assertNull(prefs)
    }

    @Test
    fun testCreateOrGet_throwsGeneralSecurityException() {
        ShadowEncryptedSharedPreferences.throwGeneralSecurityException = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = EncryptedPrefsManager.createOrGet(context, "test_prefs_sec_fail")
        org.junit.Assert.assertNull(prefs)
    }

    @Test
    fun testCreateOrGet_throwsIOException() {
        ShadowEncryptedSharedPreferences.throwIOException = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = EncryptedPrefsManager.createOrGet(context, "test_prefs_io_fail")
        org.junit.Assert.assertNull(prefs)
    }

    @Test
    fun testCreateOrGet_cachesFailures() {
        ShadowEncryptedSharedPreferences.throwIOException = true
        val context = ApplicationProvider.getApplicationContext<Context>()

        // First attempt fails and caches the failure
        var prefs = EncryptedPrefsManager.createOrGet(context, "test_prefs_cache_fail")
        org.junit.Assert.assertNull(prefs)

        // Reset the flag so create() would succeed if called
        ShadowEncryptedSharedPreferences.throwIOException = false

        // Second attempt should return null immediately without calling create()
        prefs = EncryptedPrefsManager.createOrGet(context, "test_prefs_cache_fail")
        org.junit.Assert.assertNull(prefs)
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
        field.isAccessible = true
        val map = field.get(EncryptedPrefsManager) as Map<*, *>

        assertTrue(map.isEmpty())
    }

    @Test
    fun testPutAndGet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = requireNotNull(EncryptedPrefsManager.createOrGet(context, "test_prefs"))

        prefs.edit().putString("key", "value").commit()
        assertEquals("value", prefs.getString("key", null))
    }







    @Test
    fun testClearCache_catchesException() {
        val field = EncryptedPrefsManager::class.java.getDeclaredField("prefsInstances")
        field.isAccessible = true
        val originalMap = field.get(EncryptedPrefsManager)

        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafeField.isAccessible = true
            val unsafe = theUnsafeField.get(null)

            val staticFieldOffsetMethod = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            val staticFieldBaseMethod = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            val putObjectMethod = unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)

            val offset = staticFieldOffsetMethod.invoke(unsafe, field) as Long
            val base = staticFieldBaseMethod.invoke(unsafe, field)

            val mockMap = object : java.util.concurrent.ConcurrentHashMap<String, SharedPreferences>() {
                override fun clear() {
                    throw RuntimeException("Mocked clear exception")
                }
            }

            putObjectMethod.invoke(unsafe, base, offset, mockMap)

            // This should catch the exception and not propagate it
            EncryptedPrefsManager.clearCacheForTesting()
        } finally {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafeField.isAccessible = true
            val unsafe = theUnsafeField.get(null)

            val staticFieldOffsetMethod = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            val staticFieldBaseMethod = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            val putObjectMethod = unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)

            val offset = staticFieldOffsetMethod.invoke(unsafe, field) as Long
            val base = staticFieldBaseMethod.invoke(unsafe, field)

            putObjectMethod.invoke(unsafe, base, offset, originalMap)
        }
    }
}
