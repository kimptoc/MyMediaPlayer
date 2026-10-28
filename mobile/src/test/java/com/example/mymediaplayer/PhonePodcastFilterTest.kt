package com.example.mymediaplayer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PODCAST_GENRE
import com.example.mymediaplayer.shared.PlaylistInfo
import com.example.mymediaplayer.shared.bucketGenre
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// #530: the phone keeps podcasts out of everything but their own genre, as the service does.
@RunWith(RobolectricTestRunner::class)
class PhonePodcastFilterTest {

    private val song = MediaFileInfo(
        uriString = "content://test/song",
        displayName = "Song",
        sizeBytes = 1L,
        title = "Song",
        artist = "Band",
        album = "Shared Album",
        genre = "Rock",
        year = 1985,
        addedAtMs = 1L
    )
    private val podcast = MediaFileInfo(
        uriString = "content://test/podcast",
        displayName = "Episode",
        sizeBytes = 1L,
        title = "Episode",
        artist = "Band",
        album = "Shared Album",
        genre = "Rock",
        year = 2021,
        addedAtMs = 2L,
        isPodcast = true
    )
    private val files = listOf(song, podcast)

    @Test
    fun musicFilesOf_dropsPodcasts() {
        assertEquals(listOf(song), musicFilesOf(files))
    }

    @Test
    fun songsTabDecade_ofMusicFiles_leavesOutPodcastsFromThatDecade() {
        val podcast80s = podcast.copy(uriString = "content://test/podcast80s", year = 1987)
        val music = musicFilesOf(files + podcast80s)
        assertEquals(listOf(song), filterSongsByDecade(music, decadeLabelForYear(1985)))
        assertEquals(emptyList<MediaFileInfo>(), filterSongsByDecade(music, decadeLabelForYear(2021)))
    }

    @Test
    fun genreCounts_putPodcastsInTheirOwnGenre() {
        assertEquals(mapOf(bucketGenre("Rock") to 1, PODCAST_GENRE to 1), buildGenreCounts(files))
    }

    @Test
    fun albumAndArtistCounts_ofMusicFiles_leaveOutPodcasts() {
        val music = musicFilesOf(files)
        assertEquals(mapOf("Shared Album" to 1), buildAlbumCounts(music))
        assertEquals(mapOf("Band" to 1), buildArtistCounts(music))
    }

    @Test
    fun smartPlaylists_leaveOutPodcasts() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearPrefs(app)
        val viewModel = MainViewModel(app)
        seedUiState(
            viewModel,
            MainUiState(
                scan = ScanState(scannedFiles = files, lastScanLimit = 10),
                favoriteUris = files.map { it.uriString }.toSet(),
                isPreferencesLoading = false
            )
        )

        for (smartId in listOf(
            MainViewModel.SMART_FAVORITES,
            MainViewModel.SMART_RECENTLY_ADDED,
            MainViewModel.SMART_NOT_HEARD_RECENTLY
        )) {
            viewModel.selectPlaylist(PlaylistInfo(uriString = smartId, displayName = "$smartId.m3u"))
            assertEquals(
                smartId,
                listOf(song.uriString),
                viewModel.uiState.value.playlist.playlistSongs.map { it.uriString }
            )
        }
    }
}
