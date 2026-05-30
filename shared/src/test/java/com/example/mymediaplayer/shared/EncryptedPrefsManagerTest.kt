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
        lateinit var realObject: androidx.security.crypto.MasterKey.Builder

        @Implementation
        fun __constructor__(context: Context) {
        }

        @Implementation
        fun setKeyScheme(keyScheme: androidx.security.crypto.MasterKey.KeyScheme): androidx.security.crypto.MasterKey.Builder {
             return realObject
        }

        @Implementation
        fun build(): androidx.security.crypto.MasterKey {
            // Because MasterKey is internal and we don't have mockito available,
            // Unsafe is used to create a stub instance of MasterKey to bypass AndroidKeyStore exceptions during tests.
            val cls = androidx.security.crypto.MasterKey::class.java
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafe = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafe.isAccessible = true
            val unsafe = theUnsafe.get(null)
            val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)

            return allocateInstance.invoke(unsafe, cls) as androidx.security.crypto.MasterKey
        }
    }

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
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
