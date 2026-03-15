package com.example.mymediaplayer.shared

import org.junit.Test
import kotlin.system.measureTimeMillis

class BenchmarkTest {
    @Test
    fun testBenchmark() {
        val list = List(10000) { "   hello world $it   " }

        // warm up
        for (i in 0..10) {
            list.map { it.trim().firstOrNull()?.uppercaseChar() }
            list.map { it.firstOrNull { !it.isWhitespace() }?.uppercaseChar() }
        }

        val time1 = measureTimeMillis {
            for(i in 0..100) {
                for(item in list) {
                    val c = item.trim().firstOrNull()?.uppercaseChar()
                }
            }
        }

        val time2 = measureTimeMillis {
            for(i in 0..100) {
                for(item in list) {
                    val c = item.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
                }
            }
        }

        println("trim.firstOrNull: $time1 ms")
        println("firstOrNull { !it.isWhitespace() }: $time2 ms")
    }
}
