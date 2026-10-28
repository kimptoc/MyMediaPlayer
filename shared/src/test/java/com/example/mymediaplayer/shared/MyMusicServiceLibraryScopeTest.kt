package com.example.mymediaplayer.shared

import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// #530: search and voice search play songs only; a refresh reloads a newer saved scan.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceLibraryScopeTest {

    private val song = MediaFileInfo(
        uriString = "content://media/external/audio/media/1",
        displayName = "song.mp3",
        sizeBytes = 1L,
        title = "Love Song",
        artist = "Band",
        album = "Album",
        genre = "Rock"
    )
    private val podcast = MediaFileInfo(
        uriString = "content://media/external/audio/media/2",
        displayName = "episode.mp3",
        sizeBytes = 1L,
        title = "Love Episode",
        artist = "Band",
        album = "Album",
        genre = "Rock",
        isPodcast = true
    )
    private val recording = MediaFileInfo(
        uriString = "content://media/external/audio/media/3",
        displayName = "New Recording.m4a",
        sizeBytes = 1L,
        title = "Love Memo",
        artist = "Band",
        album = "Album",
        isRecording = true
    )

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
    }

    private fun serviceWithLibrary(): MyMusicService {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        service.mediaCacheService.addAllFiles(listOf(song, podcast, recording))
        return service
    }

    private fun focus(focus: String) = Bundle().apply { putString("android.intent.extra.focus", focus) }

    @Test
    fun searchFiles_findsSongsOnly() {
        val service = serviceWithLibrary()
        assertEquals(listOf(song), service.mediaCacheService.searchFiles("love"))
    }

    @Test
    fun voiceArtistAlbumAndTitle_playSongsOnly() {
        val service = serviceWithLibrary()
        for (f in listOf("vnd.android.cursor.dir/artist", "vnd.android.cursor.dir/album")) {
            val (matches, _) = service.findFocusedMatches("", if (f.endsWith("artist")) "Band" else "Album", focus(f))
            assertEquals(f, listOf(song), matches)
        }
        val (titleMatches, _) = service.findFocusedMatches("", "Love", focus("vnd.android.cursor.item/audio"))
        assertEquals(listOf(song), titleMatches)
    }

    @Test
    fun voiceGenre_playsPodcastsOrRecordingsOnlyWhenAskedFor() {
        val service = serviceWithLibrary()
        val genreFocus = focus("vnd.android.cursor.dir/genre")

        assertEquals(listOf(song), service.findFocusedMatches("", "Rock", genreFocus).first)
        assertEquals(listOf(podcast), service.findFocusedMatches("", "podcasts", genreFocus).first)
        assertEquals(listOf(recording), service.findFocusedMatches("", "Recordings", genreFocus).first)

        val explicit = Bundle().apply { putString("android.intent.extra.genre", "Podcast") }
        assertEquals(listOf(podcast), service.findFocusedMatches("", "", explicit).first)
    }

    @Test
    fun refreshLibrary_reloadsOnlyANewerSavedScan() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("mymediaplayer_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("scan_whole_drive", true)
            .putInt("scan_limit", 50)
            .commit()
        val wholeDevice = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // The phone saves its first scan; the service loads it.
        val phone = MediaCacheService()
        phone.addAllFiles(listOf(song))
        onIo { phone.persistCache(context, wholeDevice, 50, scannedAt = 1_000L) }
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        service.refreshLibraryFromCache()
        awaitFiles(service, listOf(song))

        // Same saved scan: nothing to reload, so files added in memory stay.
        service.mediaCacheService.addFile(recording)
        service.refreshLibraryFromCache()
        Thread.sleep(300)
        assertEquals(listOf(song, recording), service.mediaCacheService.cachedFiles)

        // The phone rescans with the same number of files; the service picks up the new scan.
        phone.clearFiles()
        phone.addAllFiles(listOf(song.copy(title = "Retagged"), podcast))
        onIo { phone.persistCache(context, wholeDevice, 50, scannedAt = 2_000L) }
        service.refreshLibraryFromCache()
        awaitFiles(service, listOf(song.copy(title = "Retagged"), podcast))
    }

    @Test
    fun refreshLibrary_whileAnotherLoadRuns_reloadsWhenItFinishes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("mymediaplayer_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("scan_whole_drive", true)
            .putInt("scan_limit", 50)
            .commit()
        val wholeDevice = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val phone = MediaCacheService()
        phone.addAllFiles(listOf(song))
        onIo { phone.persistCache(context, wholeDevice, 50, scannedAt = 1_000L) }
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        service.refreshLibraryFromCache()
        awaitFiles(service, listOf(song))

        // The phone saves a newer scan while another load holds the guard.
        phone.addFile(podcast)
        onIo { phone.persistCache(context, wholeDevice, 50, scannedAt = 2_000L) }
        assertTrue(service.tryBeginScan())
        service.refreshLibraryFromCache()
        val deadline = System.currentTimeMillis() + 5_000
        while (!service.refreshPending && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(service.refreshPending)
        assertEquals(listOf(song), service.mediaCacheService.cachedFiles)

        // When that load finishes, the newer scan is picked up.
        service.endScan()
        kotlinx.coroutines.runBlocking { service.onLibraryLoaded() }
        awaitFiles(service, listOf(song, podcast))
    }

    // Room refuses the main thread, which Robolectric runs tests on.
    private fun onIo(block: () -> Unit) = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { block() }

    private fun awaitFiles(service: MyMusicService, expected: List<MediaFileInfo>) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (service.mediaCacheService.cachedFiles.sortedBy { it.uriString } == expected) return
            Thread.sleep(20)
        }
        assertEquals(expected, service.mediaCacheService.cachedFiles.sortedBy { it.uriString })
    }
}
