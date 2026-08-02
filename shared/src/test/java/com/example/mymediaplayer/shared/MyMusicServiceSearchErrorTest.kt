package com.example.mymediaplayer.shared

import android.app.SearchManager
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceSearchErrorTest {

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
    }

    private class ExceptionThrowingIntent(
        action: String,
        private val throwOnKeys: Set<String>,
        private val keyValues: Map<String, String> = emptyMap()
    ) : Intent(action) {
        override fun getStringExtra(name: String?): String? {
            if (name != null && throwOnKeys.contains(name)) {
                throw RuntimeException("Simulated exception for key: $name")
            }
            return keyValues[name]
        }
    }

    @Test
    fun onStartCommand_whenSearchIntentExtrasThrowException_doesNotCrashAndLogsError() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        // Every key throws an exception
        val allowedKeys = setOf(
            SearchManager.QUERY,
            "android.intent.extra.focus",
            "android.intent.extra.artist",
            "android.intent.extra.album",
            "android.intent.extra.genre",
            "android.intent.extra.title",
            "android.intent.extra.playlist"
        )
        val intent = ExceptionThrowingIntent(
            "android.media.action.MEDIA_PLAY_FROM_SEARCH",
            throwOnKeys = allowedKeys
        )

        // Call onStartCommand; it should complete successfully without throwing
        val result = service.onStartCommand(intent, 0, 1)

        // It should return START_NOT_STICKY as ACTION_MEDIA_PLAY_FROM_SEARCH handles it
        assertEquals(android.app.Service.START_NOT_STICKY, result)

        // Every throwing key should have produced its own warning log entry.
        val logs = ShadowLog.getLogsForTag("MyMusicService")
        assertTrue(
            "Expected a warning for the failed search query",
            logs.any { it.msg.contains("search query extra") }
        )
        for (key in allowedKeys - SearchManager.QUERY) {
            assertTrue(
                "Expected a warning for extra '$key'",
                logs.any { it.msg.contains(key) }
            )
        }
    }

    @Test
    fun onStartCommand_whenQueryThrowsButOthersSucceed_processesOthersCorrectly() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        // Only SearchManager.QUERY throws an exception, others succeed
        val intent = ExceptionThrowingIntent(
            "android.media.action.MEDIA_PLAY_FROM_SEARCH",
            throwOnKeys = setOf(SearchManager.QUERY),
            keyValues = mapOf(
                "android.intent.extra.artist" to "ArtistName",
                "android.intent.extra.title" to "SongTitle"
            )
        )

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)

        // The query failure should be logged, but the extras that succeeded must not be.
        val logs = ShadowLog.getLogsForTag("MyMusicService")
        assertTrue(
            "Expected a warning for the failed search query",
            logs.any { it.msg.contains("search query extra") }
        )
        assertTrue(
            "Extras that were read successfully should not log a warning",
            logs.none { it.msg.contains("search intent extra") }
        )
    }

    @Test
    fun onStartCommand_whenOneExtraThrowsButOthersSucceed_processesOthersCorrectly() {
        val service = Robolectric.buildService(MyMusicService::class.java).create().get()
        // A specific allowed key throws an exception, while query and other keys succeed
        val intent = ExceptionThrowingIntent(
            "android.media.action.MEDIA_PLAY_FROM_SEARCH",
            throwOnKeys = setOf("android.intent.extra.album"),
            keyValues = mapOf(
                SearchManager.QUERY to "Jazz",
                "android.intent.extra.artist" to "Miles Davis"
            )
        )

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)

        // Only the throwing extra should be logged; the query and other extras succeeded.
        val logs = ShadowLog.getLogsForTag("MyMusicService")
        assertTrue(
            "Expected a warning for extra 'android.intent.extra.album'",
            logs.any { it.msg.contains("android.intent.extra.album") }
        )
        assertTrue(
            "Query was read successfully and should not log a warning",
            logs.none { it.msg.contains("search query extra") }
        )
    }
}
