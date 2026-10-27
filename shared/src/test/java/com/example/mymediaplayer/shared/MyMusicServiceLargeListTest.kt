package com.example.mymediaplayer.shared

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * Lists of more than 500 songs didn't fit in one Bundle, so the phone sent a single random
 * search result, or a browse ID that for most lists meant the whole library (#518 points 1
 * and 2). The phone now sends the list in parts, and the service plays exactly that list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceLargeListTest {

    private lateinit var service: MyMusicService

    private val library = (1..1_500).map {
        MediaFileInfo(uriString = "content://test/song$it", displayName = "song$it.mp3", sizeBytes = 1L, title = "Song $it")
    }
    private val list = library.take(1_200).map { it.uriString }

    @After
    fun teardown() {
        NetworkQualityChecker.testInterceptor = null
    }

    @Test
    fun aShuffledUiListOfMoreThan500SongsQueuesExactlyThatList() {
        createService()

        sendInParts { service.handlePlayUiListForTesting(it.apply { putBoolean(UriListTransfer.EXTRA_LIST_SHUFFLE, true) }) }

        val queue = service.currentPlaylistQueue().map { it.uriString }
        assertEquals(1_200, queue.size)
        assertEquals(list.toSet(), queue.toSet())
        assertNotEquals("shuffled", list, queue)
        assertEquals("plays the first song of the shuffled queue", queue.first(), service.currentMediaIdForTesting())
    }

    @Test
    fun anUnshuffledUiListKeepsItsOrder() {
        createService()

        sendInParts { service.handlePlayUiListForTesting(it.apply { putBoolean(UriListTransfer.EXTRA_LIST_SHUFFLE, false) }) }

        assertEquals(list, service.currentPlaylistQueue().map { it.uriString })
    }

    @Test
    fun shufflingMoreThan500SearchResultsShufflesAllOfThem() {
        createService()

        sendInParts(urisKey = UriListTransfer.EXTRA_SEARCH_URIS) {
            service.handlePlaySearchListForTesting(it.apply { putBoolean(UriListTransfer.EXTRA_SEARCH_SHUFFLE, true) })
        }

        val queue = service.currentPlaylistQueue().map { it.uriString }
        assertEquals(list.toSet(), queue.toSet())
        assertNotEquals(list, queue)
    }

    @Test
    fun nothingPlaysUntilTheLastPartArrives() {
        createService()
        val parts = UriListTransfer.split(list)

        parts.dropLast(1).forEachIndexed { index, part ->
            service.handlePlayUiListForTesting(partBundle(UriListTransfer.EXTRA_LIST_URIS, "t", index, parts.size, part))
        }
        idle()

        assertTrue(service.currentPlaylistQueue().isEmpty())
    }

    private fun sendInParts(urisKey: String = UriListTransfer.EXTRA_LIST_URIS, send: (Bundle) -> Unit) {
        val parts = UriListTransfer.split(list)
        parts.forEachIndexed { index, part -> send(partBundle(urisKey, "t", index, parts.size, part)) }
        idle()
    }

    private fun partBundle(urisKey: String, id: String, index: Int, count: Int, part: List<String>) = Bundle().apply {
        putStringArrayList(urisKey, ArrayList(part))
        putString(UriListTransfer.EXTRA_TRANSFER_ID, id)
        putInt(UriListTransfer.EXTRA_PART_INDEX, index)
        putInt(UriListTransfer.EXTRA_PART_COUNT, count)
        putString(UriListTransfer.EXTRA_LIST_TITLE, "Test list")
    }

    private fun createService() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        // Keeps announcement generation off the real network.
        NetworkQualityChecker.testInterceptor = okhttp3.Interceptor { throw java.io.IOException("offline") }
        MyMusicService.getPrefs(ApplicationProvider.getApplicationContext())
            .edit()
            .putBoolean("track_voice_intro_enabled", false)
            .putBoolean("track_voice_outro_enabled", false)
            .commit()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        library.forEach {
            service.mediaCacheService.addFile(it)
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(service, Uri.parse(it.uriString)),
                ShadowMediaPlayer.MediaInfo(100_000, 0)
            )
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
