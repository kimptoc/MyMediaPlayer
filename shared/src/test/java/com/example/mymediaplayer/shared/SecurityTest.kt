package com.example.mymediaplayer.shared

import android.os.Bundle
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecurityTest {

    @Test
    fun testBundle() {
        val bundle = Bundle()
        bundle.putString("android.intent.extra.focus", "123")
        bundle.putString("unknown", "456")

        val allowedKeys = setOf(
            "android.intent.extra.focus",
            "android.intent.extra.artist",
            "android.intent.extra.album",
            "android.intent.extra.genre",
            "android.intent.extra.title",
            "android.intent.extra.playlist"
        )

        val safeExtras = Bundle()
        for (key in allowedKeys) {
            val value = bundle.getString(key)
            if (value != null && value.length <= 1000) {
                safeExtras.putString(key, value)
            }
        }

        assertEquals("123", safeExtras.getString("android.intent.extra.focus"))
        assertNull(safeExtras.getString("unknown"))
    }
}
