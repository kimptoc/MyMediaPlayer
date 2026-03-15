package com.example.mymediaplayer

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MyMusicService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BluetoothAutoPlayReceiverTest {

    private lateinit var context: Application
    private lateinit var receiver: BluetoothAutoPlayReceiver
    private lateinit var prefs: android.content.SharedPreferences

    private val testAddress = "00:11:22:33:44:55"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        receiver = BluetoothAutoPlayReceiver()
        prefs = context.getSharedPreferences("mymediaplayer_prefs", Context.MODE_PRIVATE)

        // Grant BLUETOOTH_CONNECT permission for Android 12+
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        // Set up base preconditions for autoplay to proceed past early returns
        prefs.edit()
            .putBoolean("bt_autoplay_enabled", true)
            .putStringSet("bt_autoplay_addresses", setOf(testAddress))
            .putString("resume_media_uri", "content://media/test")
            .apply()
    }

    private fun createConnectedIntent(): Intent {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothDevice = adapter.getRemoteDevice(testAddress)

        return Intent(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, bluetoothDevice)
        }
    }

    @Test
    fun `when connection happens within 3 seconds of last autoplay, it is throttled`() {
        // Set last autoplay to "now" minus 1000ms
        val now = SystemClock.elapsedRealtime()
        prefs.edit().putLong("bt_last_autoplay_ms", now - 1000L).apply()

        val intent = createConnectedIntent()
        receiver.onReceive(context, intent)

        // Verify reason is 'throttled'
        assertEquals("throttled", prefs.getString("bt_last_reason", null))

        // Verify service was not started
        val shadowContext = shadowOf(context)
        val nextStartedService = shadowContext.nextStartedService
        assertEquals(null, nextStartedService)
    }

    @Test
    fun `when connection happens after 3 seconds of last autoplay, it triggers`() {
        // Set last autoplay to "now" minus 4000ms
        val now = SystemClock.elapsedRealtime()
        prefs.edit().putLong("bt_last_autoplay_ms", now - 4000L).apply()

        val intent = createConnectedIntent()
        receiver.onReceive(context, intent)

        // Verify reason is 'triggered'
        assertEquals("triggered", prefs.getString("bt_last_reason", null))

        // Verify last_autoplay_ms was updated
        val updatedLastMs = prefs.getLong("bt_last_autoplay_ms", 0L)
        assertTrue(updatedLastMs >= now)

        // Verify service was started
        val shadowContext = shadowOf(context)
        val nextStartedService = shadowContext.nextStartedService
        assertNotNull(nextStartedService)
        assertEquals(MyMusicService.ACTION_BT_AUTOPLAY, nextStartedService?.action)
    }
}
