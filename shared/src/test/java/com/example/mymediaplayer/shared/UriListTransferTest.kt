package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val drops = mutableListOf<String>()
        val assembler = UriListAssembler { drops += it }
        val parts = UriListTransfer.split(uris)
        assembler.accept("a", 0, 3, parts[0])

        assertNull(assembler.accept("a", 2, 3, parts[2]))
        assertNull("still nothing once the missing part turns up late", assembler.accept("a", 1, 3, parts[1]))
        assertEquals("Dropping transfer: expected part 1 of 3, got 2 of 3", drops.first())
    }

    @Test
    fun assembler_reportsAnUnfinishedTransferReplacedByANewOne() {
        val drops = mutableListOf<String>()
        val assembler = UriListAssembler { drops += it }
        assembler.accept("old", 0, 3, UriListTransfer.split(uris)[0])

        assembler.accept("new", 0, 1, listOf("content://new"))

        assertEquals(listOf("Dropping unfinished transfer: got 1 of 3 parts before a new list started"), drops)
    }

    @Test
    fun assembler_reportsNothingForACompleteTransfer() {
        val drops = mutableListOf<String>()
        val assembler = UriListAssembler { drops += it }

        UriListTransfer.split(uris).forEachIndexed { index, part -> assembler.accept("a", index, 3, part) }
        assembler.accept("b", 0, 1, listOf("content://next"))

        assertTrue(drops.isEmpty())
    }
}
