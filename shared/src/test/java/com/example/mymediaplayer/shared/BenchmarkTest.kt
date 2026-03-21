package com.example.mymediaplayer.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis
import android.net.Uri
import org.robolectric.fakes.RoboCursor
import android.provider.MediaStore
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.atomic.AtomicInteger
import android.database.Cursor
import org.robolectric.shadows.ShadowContentResolver
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BenchmarkTest {

    @Test
    fun benchmarkLoadWholeDriveGenres() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val shadowResolver = shadowOf(context.contentResolver)

        val numGenres = 2000L
        // Set up cursor for Genres
        val genreCursor = RoboCursor()
        genreCursor.setColumnNames(listOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME))
        val genreResults = mutableListOf<Array<Any>>()
        for (i in 1..numGenres) {
            genreResults.add(arrayOf(i, "Genre $i"))
        }
        genreCursor.setResults(genreResults.toTypedArray())
        shadowResolver.setCursor(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, genreCursor)

        // Set up cursors for Members
        for (i in 1..numGenres) {
            val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", i)
            val memberCursor = RoboCursor()
            memberCursor.setColumnNames(listOf(MediaStore.Audio.Genres.Members.AUDIO_ID))
            val memberResults = mutableListOf<Array<Any>>()
            for (j in 1..50L) { // 50 members per genre
                memberResults.add(arrayOf((i * 1000 + j)))
            }
            memberCursor.setResults(memberResults.toTypedArray())
            shadowResolver.setCursor(membersUri, memberCursor)
        }

        val service = MediaCacheService()
        // Assume 5000 audio ids
        val audioIds = (1..5000L).map { it * 1000 + 1 }.toSet()
        val method = service.javaClass.getDeclaredMethod("loadWholeDriveGenres", Context::class.java, Set::class.java)
        method.isAccessible = true

        val time1 = measureTimeMillis {
            method.invoke(service, context, audioIds)
        }

        println("benchmarkLoadWholeDriveGenres refactored took: $time1 ms")
    }
}
