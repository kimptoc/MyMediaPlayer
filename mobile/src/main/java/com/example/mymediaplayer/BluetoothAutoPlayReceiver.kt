package com.example.mymediaplayer

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.mymediaplayer.shared.MyMusicService

class BluetoothAutoPlayReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "mymediaplayer_prefs"
        private const val KEY_BT_AUTOPLAY_ENABLED = "bt_autoplay_enabled"
        private const val KEY_BT_AUTOPLAY_ADDRESSES = "bt_autoplay_addresses"
        private const val KEY_BT_AUTOPLAY_DEVICES = "bt_autoplay_devices"
        private const val KEY_BT_LAST_AUTOPLAY_MS = "bt_last_autoplay_ms"
        private const val KEY_BT_LAST_EVENT_MS = "bt_last_event_ms"
        private const val KEY_BT_LAST_REASON = "bt_last_reason"
        private const val KEY_BT_LAST_DEVICE = "bt_last_device"
        private const val KEY_BT_LAST_DEVICE_NAME = "bt_last_device_name"
        private const val KEY_RESUME_MEDIA_URI = "resume_media_uri"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_BT_AUTOPLAY_ENABLED, false)) {
            record(prefs, "disabled")
            return
        }
        if (!hasBluetoothConnectPermission(context)) {
            record(prefs, "no_permission")
            return
        }

        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: run {
            record(prefs, "no_device")
            return
        }

        val address = runCatching { device.address }.getOrNull() ?: run {
            record(prefs, "no_address")
            return
        }
        val allowlist = trustedAddresses(prefs)
        @android.annotation.SuppressLint("MissingPermission")
        val name = runCatching { device.name }.getOrNull()
        if (address !in allowlist) {
            record(prefs, "untrusted_device", address, name)
            return
        }

        val hasResumeTrack = !prefs.getString(KEY_RESUME_MEDIA_URI, null).isNullOrBlank()
        if (!hasResumeTrack) {
            record(prefs, "no_resume_track", address, name)
            return
        }

        val now = SystemClock.elapsedRealtime()
        val last = prefs.getLong(KEY_BT_LAST_AUTOPLAY_MS, 0L)
        if (now - last < 3000L) {
            record(prefs, "throttled", address, name)
            return
        }
        prefs.edit().putLong(KEY_BT_LAST_AUTOPLAY_MS, now).apply()

        val serviceIntent = Intent(context, MyMusicService::class.java).apply {
            action = MyMusicService.ACTION_BT_AUTOPLAY
        }
        runCatching { context.startService(serviceIntent) }
            .onFailure {
                runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
            }
        record(prefs, "triggered", address, name)
    }

    private fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun record(
        prefs: android.content.SharedPreferences,
        reason: String,
        device: String? = null,
        deviceName: String? = null
    ) {
        prefs.edit()
            .putLong(KEY_BT_LAST_EVENT_MS, SystemClock.elapsedRealtime())
            .putString(KEY_BT_LAST_REASON, reason)
            .putString(KEY_BT_LAST_DEVICE, device)
            .putString(KEY_BT_LAST_DEVICE_NAME, deviceName)
            .apply()
    }

    private fun trustedAddresses(prefs: android.content.SharedPreferences): Set<String> {
        val out = mutableSetOf<String>()
        val raw = prefs.getString(KEY_BT_AUTOPLAY_DEVICES, null).orEmpty()
        if (raw.isNotBlank()) {
            raw.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val address = line.substringBefore('\t').trim()
                if (address.isNotBlank()) out.add(address)
            }
        }
        out.addAll(prefs.getStringSet(KEY_BT_AUTOPLAY_ADDRESSES, emptySet()) ?: emptySet())
        return out
    }
}
