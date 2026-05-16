package com.example.mymediaplayer.shared
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.media.MediaBrowserServiceCompat

fun test(context: Context, callerPackageName: String) {
    val pm = context.packageManager
    val callerUid = pm.getPackageUid(callerPackageName, 0)
}
