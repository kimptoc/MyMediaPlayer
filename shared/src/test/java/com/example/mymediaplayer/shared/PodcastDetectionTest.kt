package com.example.mymediaplayer.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastDetectionTest {

    @Test
    fun genreTagsDetectPodcasts() {
        assertTrue(isPodcastMedia("Podcast", "/Music/Show/ep1.mp3"))
        assertTrue(isPodcastMedia("podcast", null))
        assertTrue(isPodcastMedia("Audiobook", null))
        assertTrue(isPodcastMedia("Spoken Word", null))
        assertTrue(isPodcastMedia("News;Podcast", null))
    }

    @Test
    fun spokenSubstringDoesNotMatchUnrelatedWords() {
        assertFalse(isPodcastMedia("Unspoken", null))
        assertFalse(isPodcastMedia("Outspoken", null))
    }

    @Test
    fun pathDetectsPodcasts() {
        assertTrue(isPodcastMedia(null, "/storage/Podcasts/show.mp3"))
        assertTrue(isPodcastMedia("Rock", "/storage/Podcasts/show.mp3"))
        assertTrue(isPodcastMedia(null, "content://.../podcast-feeds/ep.mp3"))
    }

    @Test
    fun musicFilesAreNotPodcasts() {
        assertFalse(isPodcastMedia("Rock", "/Music/AC-DC/Highway.mp3"))
        assertFalse(isPodcastMedia("Jazz", null))
        assertFalse(isPodcastMedia(null, "/Music/Artist/Album/song.mp3"))
    }

    @Test
    fun emptyAndNullInputsAreNotPodcasts() {
        assertFalse(isPodcastMedia(null, null))
        assertFalse(isPodcastMedia("", ""))
        assertFalse(isPodcastMedia("", null))
        assertFalse(isPodcastMedia(null, ""))
    }
}
