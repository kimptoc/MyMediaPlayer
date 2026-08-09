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
private const val KEY_BT_AUTOPLAY_ADDRESSES = "bt_autoplay_addresses"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(33))
class MainActivityBluetoothParsingTest {

    @Test
    fun readTrustedBluetoothDevices_matchesReferenceParsingForEdgeCases() {
        val cases = listOf(
            Case("empty raw string", ""),
            Case("blank/whitespace-only raw string", "   "),
            Case("single entry, no trailing newline", "AA:BB:CC:DD:EE:01\tLiving Room"),
            Case("trailing newline", "AA:BB:CC:DD:EE:01\tLiving Room\n"),
            Case("crlf line ending", "AA:BB:CC:DD:EE:01\tLiving Room\r\nAA:BB:CC:DD:EE:02\tKitchen"),
            Case("no tab, address only", "AA:BB:CC:DD:EE:02"),
            Case("empty name after tab", "AA:BB:CC:DD:EE:03\t"),
            Case("padded whitespace around fields", "  AA:BB:CC:DD:EE:04  \t  Spaced Name  "),
            Case(
                "blank lines interspersed",
                "AA:BB:CC:DD:EE:01\tLiving Room\n\n   \nAA:BB:CC:DD:EE:02\tKitchen"
            ),
            Case(
                "duplicate address, last wins",
                "AA:BB:CC:DD:EE:01\tLiving Room\nAA:BB:CC:DD:EE:01\tLiving Room (renamed)"
            ),
            Case(
                "legacy address set adds a device absent from the new raw string",
                raw = "AA:BB:CC:DD:EE:01\tLiving Room",
                legacyAddresses = setOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:99")
            ),
            Case(
                "legacy address set only, no new raw string",
                raw = "",
                legacyAddresses = setOf("AA:BB:CC:DD:EE:99")
            )
        )

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val method = MainActivity::class.java.getDeclaredMethod(
            "readTrustedBluetoothDevices",
            SharedPreferences::class.java
        )
        method.isAccessible = true

        for (case in cases) {
            prefs.edit()
                .putString(KEY_BT_AUTOPLAY_DEVICES, case.raw)
                .putStringSet(KEY_BT_AUTOPLAY_ADDRESSES, case.legacyAddresses)
                .apply()

            @Suppress("UNCHECKED_CAST")
            val production = method.invoke(activity, prefs) as Map<String, String?>

            assertEquals(
                "mismatch for case: ${case.label}",
                referenceParse(case.raw, case.legacyAddresses),
                production
            )
        }
    }

    private data class Case(
        val label: String,
        val raw: String,
        val legacyAddresses: Set<String> = emptySet()
    )

    // Simple lineSequence/split reference parser plus the legacy-address-set merge,
    // independent of the production indexOf/substring implementation under test.
    private fun referenceParse(raw: String, legacyAddresses: Set<String>): Map<String, String?> {
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
        legacyAddresses.forEach { address ->
            if (address.isNotBlank() && address !in decoded) {
                decoded[address] = null
            }
        }
        return decoded
    }
}
