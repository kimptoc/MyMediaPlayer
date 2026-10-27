package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UriListTransferTest {

    private val uris = (1..1_200).map { "content://test/song$it" }

    @Test
    fun split_breaksAListIntoPartsOfAtMost500() {
        val parts = UriListTransfer.split(uris)

        assertEquals(listOf(500, 500, 200), parts.map { it.size })
        assertEquals(uris, parts.flatten())
    }

    @Test
    fun split_sendsASmallListAsOnePart() {
        assertEquals(listOf(uris.take(3)), UriListTransfer.split(uris.take(3)))
    }

    @Test
    fun assembler_returnsTheWholeListOnlyAfterTheLastPart() {
        val assembler = UriListAssembler()
        val parts = UriListTransfer.split(uris)

        assertNull(assembler.accept("a", 0, 3, parts[0]))
        assertNull(assembler.accept("a", 1, 3, parts[1]))
        assertEquals(uris, assembler.accept("a", 2, 3, parts[2]))
    }

    @Test
    fun assembler_dropsAnUnfinishedTransferWhenANewOneStarts() {
        val assembler = UriListAssembler()
        val parts = UriListTransfer.split(uris)
        assembler.accept("old", 0, 3, parts[0])

        assertEquals(listOf("content://new"), assembler.accept("new", 0, 1, listOf("content://new")))
        assertNull("a late part of the old transfer is ignored", assembler.accept("old", 1, 3, parts[1]))
    }

    @Test
    fun assembler_givesUpWhenAPartIsMissing() {
        val assembler = UriListAssembler()
        val parts = UriListTransfer.split(uris)
        assembler.accept("a", 0, 3, parts[0])

        assertNull(assembler.accept("a", 2, 3, parts[2]))
        assertNull("still nothing once the missing part turns up late", assembler.accept("a", 1, 3, parts[1]))
    }
}
