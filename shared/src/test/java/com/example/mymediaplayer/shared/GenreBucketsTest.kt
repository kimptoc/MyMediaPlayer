package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreBucketsTest {

    @Test
    fun bucketGenre_groupsCommonSubgenres() {
        assertEquals("Rock/Metal", bucketGenre("Alternative Rock"))
        assertEquals("Hip-Hop/Rap", bucketGenre("Hip-Hop"))
        assertEquals("R&B/Soul", bucketGenre("Soul"))
        assertEquals("Jazz/Blues", bucketGenre("Smooth Jazz"))
        assertEquals("Jazz/Blues", bucketGenre("Delta Blues"))
        assertEquals("Electronic/Dance", bucketGenre("Drum and Bass"))
        assertEquals("Other", bucketGenre("Original Soundtrack"))
        assertEquals("Country/Folk", bucketGenre("Bluegrass"))
        assertEquals("Latin", bucketGenre("Reggaeton"))
        assertEquals("Reggae", bucketGenre("Reggae"))
        assertEquals("Reggae", bucketGenre("Dancehall"))
        assertEquals("Reggae", bucketGenre("Ska"))
        assertEquals("Reggae", bucketGenre("Dub"))
        assertEquals("Other", bucketGenre("Dub Techno"))
        assertEquals("Other", bucketGenre("Post-Blackgaze-Experimental"))
        assertEquals("Other", bucketGenre(null))
    }
}
