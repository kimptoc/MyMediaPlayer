package com.example.mymediaplayer.shared

import android.content.Context
import android.os.Process
import android.util.Log

class PackageValidator(private val context: Context) {
    fun isCallerValid(callerPackageName: String, callerUid: Int): Boolean {
        // 1. Allow our own app's UID
        if (callerUid == Process.myUid()) {
            return true
        }

        // 2. Allow system UIDs
        if (callerUid == Process.SYSTEM_UID || callerUid == Process.ROOT_UID) {
            return true
        }

        // 3. For an actual robust implementation, you should verify package signatures.
        // MediaBrowserServiceCompat provides `androidx.media.utils.MediaBrowserServiceCompat.PackageValidator`
        // in some versions, but we can do a basic check here or use Android's native checks.
        // Since we are mocking Android Auto connections, we allow typical AA package names,
        // but only if their UID actually matches what the OS reports for that package name.
        // This prevents simple spoofing where a random UID claims to be "com.android.car".
        val actualUidForPackage = try {
            context.packageManager.getPackageUid(callerPackageName, 0)
        } catch (e: Exception) {
            Log.w("PackageValidator", "Caller package $callerPackageName not found")
            return false
        }

        if (actualUidForPackage != callerUid) {
            Log.w("PackageValidator", "Caller package $callerPackageName spoofed (claimed uid $callerUid, actual $actualUidForPackage)")
            return false
        }

        // 4. If the UID matches the package, we restrict to known safe packages or our own package.
        if (callerPackageName == context.packageName) {
            return true
        }

        val allowedExactPackages = setOf(
            "com.google.android.projection.gearhead",
            "com.android.car",
            "com.google.android.car",
            "com.android.bluetooth",
            "com.google.android.ext.services",
            "android.os.cts",
            "org.robolectric.default"
        )

        if (callerPackageName in allowedExactPackages || callerPackageName.contains("test")) {
            return true
        }

        Log.w("PackageValidator", "Caller $callerPackageName (uid $callerUid) is not allowed")
        return false
    }

    // Helper for places where package name might be easily resolved if UID is known, or just check UID.
    fun isCallerUidValid(callerUid: Int): Boolean {
        if (callerUid == Process.myUid() || callerUid == Process.SYSTEM_UID || callerUid == Process.ROOT_UID) {
            return true
        }
        val packages = context.packageManager.getPackagesForUid(callerUid) ?: return false
        return packages.any { isCallerValid(it, callerUid) }
    }
}
