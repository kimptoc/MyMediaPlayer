package com.example.mymediaplayer

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PREFS_NAME = "mymediaplayer_prefs"
private const val KEY_BT_AUTOPLAY_DEVICES = "bt_autoplay_devices"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(33))
class MainActivityBluetoothParsingTest {

    @Test
    fun readTrustedBluetoothDevices_matchesReferenceParsingForEdgeCases() {
        val cases = listOf(
            "empty raw string" to "",
            "blank/whitespace-only raw string" to "   ",
            "single entry, no trailing newline" to "AA:BB:CC:DD:EE:01\tLiving Room",
            "trailing newline" to "AA:BB:CC:DD:EE:01\tLiving Room\n",
            "crlf line ending" to "AA:BB:CC:DD:EE:01\tLiving Room\r\nAA:BB:CC:DD:EE:02\tKitchen",
            "no tab, address only" to "AA:BB:CC:DD:EE:02",
            "empty name after tab" to "AA:BB:CC:DD:EE:03\t",
            "padded whitespace around fields" to "  AA:BB:CC:DD:EE:04  \t  Spaced Name  ",
            "blank lines interspersed" to "AA:BB:CC:DD:EE:01\tLiving Room\n\n   \nAA:BB:CC:DD:EE:02\tKitchen",
            "duplicate address, last wins" to
                "AA:BB:CC:DD:EE:01\tLiving Room\nAA:BB:CC:DD:EE:01\tLiving Room (renamed)"
        )

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val method = MainActivity::class.java.getDeclaredMethod(
            "readTrustedBluetoothDevices",
            SharedPreferences::class.java
        )
        method.isAccessible = true

        for ((label, raw) in cases) {
            prefs.edit().putString(KEY_BT_AUTOPLAY_DEVICES, raw).apply()

            @Suppress("UNCHECKED_CAST")
            val production = method.invoke(activity, prefs) as Map<String, String?>

            assertEquals("mismatch for case: $label", referenceParse(raw), production)
        }
    }

    // Simple lineSequence/split reference parser, independent of the production
    // indexOf/substring implementation under test.
    private fun referenceParse(raw: String): Map<String, String?> {
        val decoded = mutableMapOf<String, String?>()
        if (raw.isNotBlank()) {
            raw.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('\t', limit = 2)
                val address = parts[0].trim()
                if (address.isBlank()) return@forEach
                val name = parts.getOrNull(1)?.trim()?.ifBlank { null }
                decoded[address] = name
            }
        }
        return decoded
    }
}
