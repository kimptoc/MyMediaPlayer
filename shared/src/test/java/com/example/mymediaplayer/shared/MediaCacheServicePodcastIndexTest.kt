package com.example.mymediaplayer.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCacheServicePodcastIndexTest {

    private fun mediaFile(
        uri: String,
        title: String,
        artist: String?,
        album: String?,
        genre: String?,
        year: Int?,
        isPodcast: Boolean
    ) = MediaFileInfo(
        uriString = uri,
        displayName = "$title.mp3",
        sizeBytes = 1L,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = 1000L,
        year = year,
        isPodcast = isPodcast
    )

    @Test
    fun podcastsLandOnlyInPodcastsGenre() {
        val service = MediaCacheService()
        val music = mediaFile(
            uri = "file:///Music/AC-DC/Highway.mp3",
            title = "Highway",
            artist = "AC/DC",
            album = "Highway to Hell",
            genre = "Rock",
            year = 1979,
            isPodcast = false
        )
        val podcast = mediaFile(
            uri = "file:///Podcasts/show/ep1.mp3",
            title = "Ep1",
            artist = "Host",
            album = "Show",
            genre = "Podcast",
            year = 2024,
            isPodcast = true
        )
        service.addAllFiles(listOf(music, podcast))

        service.buildAlbumArtistIndexesFromCache()

        assertTrue("Podcasts genre present", service.genres().contains(PODCAST_GENRE))
        assertEquals(listOf(podcast), service.songsForGenre(PODCAST_GENRE))

        assertFalse("podcast album hidden", service.albums().contains("Show"))
        assertFalse("podcast artist hidden", service.artists().contains("Host"))
        assertFalse(
            "podcast decade hidden",
            service.decades().contains("2020s") &&
                service.songsForDecade("2020s").any { it.uriString == podcast.uriString }
        )
        for (g in service.genres()) {
            if (g == PODCAST_GENRE) continue
            assertTrue(
                "non-podcast genre '$g' contains a podcast",
                service.songsForGenre(g).none { it.isPodcast }
            )
        }

        assertTrue("music album indexed", service.albums().contains("Highway to Hell"))
        assertTrue("music artist indexed", service.artists().contains("AC/DC"))
        assertTrue("music decade indexed", service.decades().contains("1970s"))
        assertTrue(
            "music in rock genre",
            service.songsForGenre("Rock/Metal").any { it.uriString == music.uriString }
        )
    }

    @Test
    fun cachedMusicFilesExcludesPodcasts() {
        val service = MediaCacheService()
        val music = mediaFile(
            uri = "file:///Music/song.mp3",
            title = "song",
            artist = "A",
            album = "Al",
            genre = "Rock",
            year = 2000,
            isPodcast = false
        )
        val podcast = mediaFile(
            uri = "file:///Podcasts/ep.mp3",
            title = "ep",
            artist = "H",
            album = "S",
            genre = "Podcast",
            year = 2024,
            isPodcast = true
        )
        service.addAllFiles(listOf(music, podcast))

        assertEquals(listOf(music), service.cachedMusicFiles)
        assertEquals(2, service.cachedFiles.size)
    }
}
