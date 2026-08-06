package com.example.mymediaplayer.shared

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowPackageManager
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import androidx.media.MediaSessionManager

@Implements(MediaSessionManager::class)
class ShadowMediaSessionManager {
    companion object {
        var throwException = false
    }

    @Implementation
    fun isTrustedForMediaControl(info: MediaSessionManager.RemoteUserInfo): Boolean {
        if (throwException) {
            throw RuntimeException("Simulated MediaSessionManager failure")
        }
        // Mock the trust logic for testing:
        // Trust known packages unless it's a known attacker UID.
        val trustedPackages = setOf(
            "com.google.android.projection.gearhead",
            "com.android.car",
            "com.google.android.car",
            "com.android.bluetooth",
            "com.google.android.ext.services",
            "android.os.cts",
            "org.robolectric.default"
        )
        return info.packageName in trustedPackages && info.uid != 30000
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMediaSessionManager::class])
class PackageValidatorTest {

    private lateinit var context: Context
    private lateinit var validator: PackageValidator
    private lateinit var pm: ShadowPackageManager

    @Before
    fun setup() {
        ShadowMediaSessionManager.throwException = false
        context = RuntimeEnvironment.getApplication()
        validator = PackageValidator(context)
        pm = shadowOf(context.packageManager)
    }

    @Test
    fun isCallerValid_allowsSameUid() {
        assertTrue(validator.isCallerValid("some.random.package", Process.myUid()))
    }

    @Test
    fun isCallerValid_allowsSamePackage() {
        val uid = 12345
        pm.installPackage(PackageInfo().apply { packageName = context.packageName })
        pm.setPackagesForUid(uid, context.packageName)
        assertTrue(validator.isCallerValid(context.packageName, uid))
    }

    @Test
    fun isCallerValid_allowsSystemPackagesWithSignatureManager() {
        val uid1 = 10001
        pm.installPackage(PackageInfo().apply { packageName = "com.google.android.projection.gearhead" })
        pm.setPackagesForUid(uid1, "com.google.android.projection.gearhead")
        assertTrue(validator.isCallerValid("com.google.android.projection.gearhead", uid1))
    }

    @Test
    fun isCallerValid_rejectsSpoofedPackage() {
        val realUid = 20000
        val attackerUid = 30000
        pm.installPackage(PackageInfo().apply { packageName = "com.google.android.projection.gearhead" })
        pm.setPackagesForUid(realUid, "com.google.android.projection.gearhead")

        // Attacker claims to be gearhead but has a different UID
        assertFalse(validator.isCallerValid("com.google.android.projection.gearhead", attackerUid))
    }

    @Test
    fun isCallerValid_rejectsUnknownPackages() {
        val uid = 40000
        pm.installPackage(PackageInfo().apply { packageName = "com.malicious.app" })
        pm.setPackagesForUid(uid, "com.malicious.app")
        assertFalse(validator.isCallerValid("com.malicious.app", uid))
    }

    @Test
    fun isCallerValid_packageNotFound_returnsFalse() {
        assertFalse(validator.isCallerValid("com.not.found.package", 50000))
    }

    @Test
    fun isCallerUidValid_allowsSameUid() {
        assertTrue(validator.isCallerUidValid(Process.myUid()))
    }

    @Test
    fun isCallerUidValid_allowsSystemUid() {
        assertTrue(validator.isCallerUidValid(Process.SYSTEM_UID))
        assertTrue(validator.isCallerUidValid(Process.ROOT_UID))
    }

    @Test
    fun isCallerUidValid_returnsFalseWhenPackagesIsNull() {
        val uid = 60000
        assertFalse(validator.isCallerUidValid(uid))
    }

    @Test
    fun isCallerUidValid_returnsTrueWhenValidPackageExistsForUid() {
        val uid = 70000
        pm.installPackage(PackageInfo().apply { packageName = "com.google.android.projection.gearhead" })
        pm.setPackagesForUid(uid, "com.google.android.projection.gearhead")
        assertTrue(validator.isCallerUidValid(uid))
    }

    @Test
    fun isCallerUidValid_returnsFalseWhenOnlyInvalidPackagesExistForUid() {
        val uid = 80000
        pm.installPackage(PackageInfo().apply { packageName = "com.malicious.app" })
        pm.setPackagesForUid(uid, "com.malicious.app")
        assertFalse(validator.isCallerUidValid(uid))
    }

    @Test
    fun isCallerValid_sanitizesNewlinesInPackageName() {
        // Invokes isCallerValid with a package name containing newline characters and verifies
        // both that it runs safely and that the logged package name has no raw newlines in it.
        val uid = 90000
        val maliciousPackageName = "com.malicious.app\nwith\nnewlines"
        pm.installPackage(PackageInfo().apply { packageName = maliciousPackageName })
        pm.setPackagesForUid(uid, maliciousPackageName)

        assertFalse(validator.isCallerValid(maliciousPackageName, uid))

        val logs = ShadowLog.getLogsForTag("PackageValidator")
        assertTrue(logs.isNotEmpty())
        logs.forEach { entry ->
            assertFalse(entry.msg.contains("\n"))
            assertFalse(entry.msg.contains("\r"))
        }
    }

    @Test
    fun isCallerValid_whenMediaSessionManagerThrows_returnsFalseAndLogsException() {
        ShadowMediaSessionManager.throwException = true
        val uid = 10001
        val packageName = "com.google.android.projection.gearhead"
        pm.installPackage(PackageInfo().apply { this.packageName = packageName })
        pm.setPackagesForUid(uid, packageName)

        assertFalse(validator.isCallerValid(packageName, uid))

        val logs = ShadowLog.getLogsForTag("PackageValidator")
        val errorLog = logs.find { it.msg.contains("Failed to verify caller using MediaSessionManager") }
        assertTrue("Expected error log not found", errorLog != null)
        assertEquals(android.util.Log.ERROR, errorLog!!.type)
        assertTrue(
            "Expected the caught exception to be attached to the log entry",
            errorLog.throwable is RuntimeException
        )
        assertEquals("Simulated MediaSessionManager failure", errorLog.throwable?.message)
    }
}
