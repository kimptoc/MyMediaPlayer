package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MyMusicServiceGenreCountsTest {

    private fun mediaFile(genre: String?, isPodcast: Boolean) = MediaFileInfo(
        uriString = "file:///${if (isPodcast) "podcast" else "song"}-${genre.orEmpty()}",
        displayName = "x.mp3",
        sizeBytes = 1L,
        title = "x",
        genre = genre,
        isPodcast = isPodcast
    )

    @Test
    fun podcastsCountUnderPodcastsBucket() {
        val service = MyMusicService()
        val method = MyMusicService::class.java.getDeclaredMethod(
            "buildGenreCounts",
            List::class.java
        )
        method.isAccessible = true

        val files = listOf(
            mediaFile("Rock", isPodcast = false),
            mediaFile("Hip-Hop", isPodcast = false),
            mediaFile("Podcast", isPodcast = true),
            mediaFile("Audiobook", isPodcast = true),
            mediaFile("Spoken Word", isPodcast = true)
        )

        @Suppress("UNCHECKED_CAST")
        val counts = method.invoke(service, files) as Map<String, Int>

        assertEquals(3, counts[PODCAST_GENRE])
        assertEquals(1, counts[bucketGenre("Rock")])
        assertEquals(1, counts[bucketGenre("Hip-Hop")])
    }
}
