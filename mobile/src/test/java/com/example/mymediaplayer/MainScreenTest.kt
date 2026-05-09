package com.example.mymediaplayer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

class MainScreenTest {

    @Test
    fun testFormatDuration() {
        val method: Method = Class.forName("com.example.mymediaplayer.MainScreenKt")
            .getDeclaredMethod("formatDuration", Long::class.java)
        method.isAccessible = true

        assertEquals("", method.invoke(null, 0L))
        assertEquals("", method.invoke(null, -100L))
        assertEquals("0:01", method.invoke(null, 1000L))
        assertEquals("0:59", method.invoke(null, 59999L))
        assertEquals("1:00", method.invoke(null, 60000L))
        assertEquals("1:01", method.invoke(null, 61000L))
        assertEquals("10:05", method.invoke(null, 605000L))
        assertEquals("60:00", method.invoke(null, 3600000L))
    }
}
