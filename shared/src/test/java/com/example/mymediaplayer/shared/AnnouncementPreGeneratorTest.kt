package com.example.mymediaplayer.shared

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnnouncementPreGeneratorTest {

    @Test
    fun `getStockPhrase returns valid intro string`() {
        val generator = AnnouncementPreGenerator(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.Unconfined))
        val phrase = generator.getStockPhrase("Bohemian Rhapsody", "Queen", true)

        val possiblePhrases = listOf(
            "Up next: Bohemian Rhapsody by Queen",
            "Here's Bohemian Rhapsody by Queen",
            "Bohemian Rhapsody is coming up by Queen",
            "Now playing: Bohemian Rhapsody by Queen"
        )

        assertTrue(possiblePhrases.contains(phrase))
    }

    @Test
    fun `getStockPhrase returns valid outro string`() {
        val generator = AnnouncementPreGenerator(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.Unconfined))
        val phrase = generator.getStockPhrase("Bohemian Rhapsody", "Queen", false)

        val possiblePhrases = listOf(
            "That was Bohemian Rhapsody by Queen",
            "Thanks for listening to Bohemian Rhapsody by Queen",
            "Bohemian Rhapsody is done by Queen"
        )

        assertTrue(possiblePhrases.contains(phrase))
    }

    @Test
    fun `getStockPhrase handles null artist`() {
        val generator = AnnouncementPreGenerator(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.Unconfined))
        val phrase = generator.getStockPhrase("Song 2", null, true)

        val possiblePhrases = listOf(
            "Up next: Song 2 by Unknown",
            "Here's Song 2 by Unknown",
            "Song 2 is coming up by Unknown",
            "Now playing: Song 2 by Unknown"
        )

        assertTrue(possiblePhrases.contains(phrase))
    }
}
