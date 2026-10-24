package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsBackendTest {

    @Test
    fun fromStorage_mapsKnownValues() {
        assertEquals(TtsBackend.STANDARD, TtsBackend.fromStorage("standard"))
        assertEquals(TtsBackend.CLOUD_GEMINI, TtsBackend.fromStorage("cloud_gemini"))
        assertEquals(TtsBackend.GEMINI_API, TtsBackend.fromStorage("gemini_api"))
    }

    @Test
    fun fromStorage_defaultsToStandardForUnknownOrNull() {
        assertEquals(TtsBackend.STANDARD, TtsBackend.fromStorage(null))
        assertEquals(TtsBackend.STANDARD, TtsBackend.fromStorage(""))
        assertEquals(TtsBackend.STANDARD, TtsBackend.fromStorage("garbage"))
    }
}
