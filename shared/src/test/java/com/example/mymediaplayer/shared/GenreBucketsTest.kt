package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreBucketsTest {

    @Test
    fun bucketGenre_groupsCommonSubgenres() {
        assertEquals("Rock", bucketGenre("Alternative Rock"))
        assertEquals("Hip-Hop/Rap", bucketGenre("Hip-Hop"))
        assertEquals("R&B/Soul", bucketGenre("Soul"))
        assertEquals("Jazz", bucketGenre("Smooth Jazz"))
        assertEquals("Blues", bucketGenre("Delta Blues"))
        assertEquals("Electronic", bucketGenre("Drum and Bass"))
        assertEquals("Soundtrack", bucketGenre("Original Soundtrack"))
        assertEquals("Other", bucketGenre(null))
    }
}
