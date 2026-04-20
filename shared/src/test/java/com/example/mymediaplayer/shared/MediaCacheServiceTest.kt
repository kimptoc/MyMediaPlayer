package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboCursor
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCacheServiceTest {

    @Test
    fun scanDirectory_reportsProgressEvenWhenEmpty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = DocumentsContract.buildTreeDocumentUri("test", "root")
        val service = MediaCacheService()
        val progress = mutableListOf<Pair<Int, Int>>()

        service.scanDirectory(ScanContext(context, treeUri, maxFiles = 10, onProgress = { songsFound, foldersScanned ->
            progress += songsFound to foldersScanned
        }))

        assertTrue(service.cachedFiles.size <= 10)
        assertTrue(progress.isNotEmpty())
        val last = progress.last()
        assertEquals(service.cachedFiles.size, last.first)
        assertTrue(last.second >= 0)
    }

    @Test
    fun scanDirectory_canBeCancelled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeUri = DocumentsContract.buildTreeDocumentUri("test", "root")
        val service = MediaCacheService()

        val job: Job = launch {
            service.scanDirectory(ScanContext(context, treeUri, maxFiles = 10, onProgress = { _, _ -> }))
        }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
    }

    @Test
    fun albumsByLatestAddedDesc_sortsByNewestTrackPerAlbum() {
        val service = MediaCacheService()
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/a1",
                displayName = "a1.mp3",
                sizeBytes = 1L,
                album = "Album A",
                addedAtMs = 1_000L
            )
        )
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/b1",
                displayName = "b1.mp3",
                sizeBytes = 1L,
                album = "Album B",
                addedAtMs = 3_000L
            )
        )
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/a2",
                displayName = "a2.mp3",
                sizeBytes = 1L,
                album = "Album A",
                addedAtMs = 5_000L
            )
        )
        service.buildAlbumArtistIndexesFromCache()

        val albums = service.albumsByLatestAddedDesc()

        assertEquals(listOf("Album A", "Album B"), albums)
    }

    @Test
    fun isSupportedAudioFile_acceptsMoreExtensionsAndAudioMime() {
        val service = MediaCacheService()

        assertTrue(service.isSupportedAudioFile("track.wma", null))
        assertTrue(service.isSupportedAudioFile("track.alac", null))
        assertTrue(service.isSupportedAudioFile("track.ape", null))
        assertTrue(service.isSupportedAudioFile("track.m4a", "audio/mp4"))
        assertTrue(service.isSupportedAudioFile("track.bin", "audio/flac"))
        assertTrue(service.isSupportedAudioFile("track.unknown", "application/ogg"))
    }

    @Test
    fun isSupportedAudioFile_rejectsPlaylistMime() {
        val service = MediaCacheService()

        assertTrue(service.isSupportedPlaylistFile("playlist.m3u", null))
        assertTrue(service.isSupportedPlaylistFile("playlist.txt", "audio/x-mpegurl"))
        assertFalse(service.isSupportedAudioFile("playlist.m3u", "audio/x-mpegurl"))
    }

    @Test
    fun getFileIndexByUri_returnsPopulatedMap() {
        val service = MediaCacheService()
        val file1 = MediaFileInfo(uriString = "uri1", displayName = "file1", sizeBytes = 100L)
        val file2 = MediaFileInfo(uriString = "uri2", displayName = "file2", sizeBytes = 200L)

        service.addFile(file1)
        service.addFile(file2)

        val index = service.getFileIndexByUri()
        assertEquals(2, index.size)
        assertEquals(file1, index["uri1"])
        assertEquals(file2, index["uri2"])
    }

    @Test
    fun enrichGenresFromMediaStore_enrichesGenresCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowResolver = shadowOf(context.contentResolver)

        val mediaCursor = RoboCursor()
        mediaCursor.setColumnNames(listOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME))
        mediaCursor.setResults(arrayOf(arrayOf(1L, "song1.mp3")))
        shadowResolver.setCursor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaCursor)

        val genresCursor = RoboCursor()
        genresCursor.setColumnNames(listOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME))
        genresCursor.setResults(arrayOf(arrayOf(100L, "Rock")))
        shadowResolver.setCursor(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, genresCursor)

        val membersCursor = RoboCursor()
        membersCursor.setColumnNames(listOf(MediaStore.Audio.Genres.Members.AUDIO_ID))
        membersCursor.setResults(arrayOf(arrayOf(1L)))
        val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", 100L)
        shadowResolver.setCursor(membersUri, membersCursor)

        val service = MediaCacheService()
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "song1.mp3",
                sizeBytes = 100L,
                genre = null
            )
        )

        service.enrichGenresFromMediaStore(context)

        assertEquals("Rock", service.cachedFiles.first().genre)
    }

    @Test
    fun enrichGenresFromMediaStore_doesNotOverwriteExistingGenres() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowResolver = shadowOf(context.contentResolver)

        val mediaCursor = RoboCursor()
        mediaCursor.setColumnNames(listOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME))
        mediaCursor.setResults(arrayOf(arrayOf(1L, "song1.mp3")))
        shadowResolver.setCursor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaCursor)

        val genresCursor = RoboCursor()
        genresCursor.setColumnNames(listOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME))
        genresCursor.setResults(arrayOf(arrayOf(100L, "Pop")))
        shadowResolver.setCursor(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, genresCursor)

        val membersCursor = RoboCursor()
        membersCursor.setColumnNames(listOf(MediaStore.Audio.Genres.Members.AUDIO_ID))
        membersCursor.setResults(arrayOf(arrayOf(1L)))
        val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", 100L)
        shadowResolver.setCursor(membersUri, membersCursor)

        val service = MediaCacheService()
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "song1.mp3",
                sizeBytes = 100L,
                genre = "Rock"
            )
        )

        service.enrichGenresFromMediaStore(context)

        assertEquals("Rock", service.cachedFiles.first().genre)
    }

    @Test
    fun enrichGenresFromMediaStore_handlesConflictingGenres() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowResolver = shadowOf(context.contentResolver)

        val mediaCursor = RoboCursor()
        mediaCursor.setColumnNames(listOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME))
        mediaCursor.setResults(arrayOf(arrayOf(1L, "song1.mp3")))
        shadowResolver.setCursor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaCursor)

        val genresCursor = RoboCursor()
        genresCursor.setColumnNames(listOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME))
        genresCursor.setResults(arrayOf(arrayOf(100L, "Rock"), arrayOf(101L, "Electronic")))
        shadowResolver.setCursor(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, genresCursor)

        val membersCursor1 = RoboCursor()
        membersCursor1.setColumnNames(listOf(MediaStore.Audio.Genres.Members.AUDIO_ID))
        membersCursor1.setResults(arrayOf(arrayOf(1L)))
        val membersUri1 = MediaStore.Audio.Genres.Members.getContentUri("external", 100L)
        shadowResolver.setCursor(membersUri1, membersCursor1)

        val membersCursor2 = RoboCursor()
        membersCursor2.setColumnNames(listOf(MediaStore.Audio.Genres.Members.AUDIO_ID))
        membersCursor2.setResults(arrayOf(arrayOf(1L)))
        val membersUri2 = MediaStore.Audio.Genres.Members.getContentUri("external", 101L)
        shadowResolver.setCursor(membersUri2, membersCursor2)

        val service = MediaCacheService()
        service.addFile(
            MediaFileInfo(
                uriString = "content://test/song1",
                displayName = "song1.mp3",
                sizeBytes = 100L,
                genre = null
            )
        )

        service.enrichGenresFromMediaStore(context)

        assertEquals(null, service.cachedFiles.first().genre)
    }
}
