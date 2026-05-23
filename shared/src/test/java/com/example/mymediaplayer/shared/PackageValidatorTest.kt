package com.example.mymediaplayer.shared

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Process
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
class PackageValidatorTest {

    private lateinit var context: Context
    private lateinit var validator: PackageValidator
    private lateinit var pm: ShadowPackageManager

    @Before
    fun setup() {
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
    fun isCallerValid_allowsSystemPackages() {
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
    fun isCallerValid_returnsFalseWhenPackageNotFound() {
        // We simulate a package that is not installed on the system
        // getPackageUid will throw NameNotFoundException
        val uid = 50000
        assertFalse(validator.isCallerValid("com.nonexistent.package", uid))
    }
}
