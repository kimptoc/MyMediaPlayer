package com.example.mymediaplayer

import android.content.Intent
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class BluetoothAutoPlayReceiverSecurityTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun testInvalidExtraTypeBelowTiramisu() {
        val receiver = BluetoothAutoPlayReceiver()
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Put a String array instead of a BluetoothDevice
        val intent = Intent(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, arrayOf("Not a bluetooth device"))
        }

        try {
            receiver.onReceive(context, intent)
        } catch (e: Exception) {
            fail("Should not crash on invalid extra type: ${e.message}")
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun testInvalidExtraTypeTiramisu() {
        val receiver = BluetoothAutoPlayReceiver()
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Put a String array instead of a BluetoothDevice
        val intent = Intent(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, arrayOf("Not a bluetooth device"))
        }

        try {
            receiver.onReceive(context, intent)
        } catch (e: Exception) {
            fail("Should not crash on invalid extra type: ${e.message}")
        }
    }
}
