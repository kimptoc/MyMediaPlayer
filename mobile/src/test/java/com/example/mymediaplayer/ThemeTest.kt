package com.example.mymediaplayer

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun testLcarsDarkColorScheme() {
        val colorScheme = lcarsDarkColorScheme()
        assertEquals(Color(0xFF0D0D0D), colorScheme.background)
        assertEquals(Color(0xFFFF9900), colorScheme.primary)
        assertEquals(Color(0xFF0D0D0D), colorScheme.onPrimary)
        assertEquals(Color(0xFF1A1025), colorScheme.surface)
    }

    @Test
    fun testLcarsLightColorScheme() {
        val colorScheme = lcarsLightColorScheme()
        assertEquals(Color(0xFFFFF8F0), colorScheme.background)
        assertEquals(Color(0xFFCC6600), colorScheme.primary)
        assertEquals(Color.White, colorScheme.onPrimary)
        assertEquals(Color(0xFFFFF8F0), colorScheme.surface)
    }

    @Test
    fun testThemesAreDifferent() {
        val dark = lcarsDarkColorScheme()
        val light = lcarsLightColorScheme()
        assertNotEquals(dark.background, light.background)
        assertNotEquals(dark.primary, light.primary)
    }
}
