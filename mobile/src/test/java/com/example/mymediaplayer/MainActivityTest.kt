package com.example.mymediaplayer

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(33))
class MainActivityTest {

    @Test
    fun testVoiceSearchIntent_extractsExpectedExtras() {
        val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
        intent.putExtra(SearchManager.QUERY, "test query")
        intent.putExtra(MediaStore.EXTRA_MEDIA_TITLE, "test title")
        intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, "test artist")

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
        val activity = controller.create().get()

        val pendingVoiceSearchQueryField = MainActivity::class.java.getDeclaredField("pendingVoiceSearchQuery")
        pendingVoiceSearchQueryField.isAccessible = true
        val query = pendingVoiceSearchQueryField.get(activity) as? String

        assertEquals("test query", query)

        val pendingVoiceSearchExtrasField = MainActivity::class.java.getDeclaredField("pendingVoiceSearchExtras")
        pendingVoiceSearchExtrasField.isAccessible = true
        val extras = pendingVoiceSearchExtrasField.get(activity) as? Bundle

        assertNotNull(extras)
        assertEquals("test title", extras?.getString(MediaStore.EXTRA_MEDIA_TITLE))
        assertEquals("test artist", extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST))
    }

    @Test
    fun testVoiceSearchIntent_ignoresDisallowedExtras() {
        val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
        intent.putExtra(SearchManager.QUERY, "test query")
        intent.putExtra("disallowed_extra_key", "disallowed value")
        intent.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, "test album")

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
        val activity = controller.create().get()

        val pendingVoiceSearchExtrasField = MainActivity::class.java.getDeclaredField("pendingVoiceSearchExtras")
        pendingVoiceSearchExtrasField.isAccessible = true
        val extras = pendingVoiceSearchExtrasField.get(activity) as? Bundle

        assertNotNull(extras)
        assertEquals("test album", extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM))
        assertEquals(null, extras?.getString("disallowed_extra_key"))
    }

    @Test
    fun testVoiceSearchIntent_handlesMissingQuery() {
        val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
        val activity = controller.create().get()

        val pendingVoiceSearchQueryField = MainActivity::class.java.getDeclaredField("pendingVoiceSearchQuery")
        pendingVoiceSearchQueryField.isAccessible = true
        val query = pendingVoiceSearchQueryField.get(activity) as? String

        assertEquals("", query)
    }

    @Test
    fun testVoiceSearchIntent_truncatesLongQueryAndExtras() {
        val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
        val longString = "a".repeat(600)
        intent.putExtra(SearchManager.QUERY, longString)
        intent.putExtra(MediaStore.EXTRA_MEDIA_GENRE, longString)

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
        val activity = controller.create().get()

        val pendingVoiceSearchQueryField = MainActivity::class.java.getDeclaredField("pendingVoiceSearchQuery")
        pendingVoiceSearchQueryField.isAccessible = true
        val query = pendingVoiceSearchQueryField.get(activity) as? String

        assertEquals(500, query?.length)
        assertEquals("a".repeat(500), query)

        val pendingVoiceSearchExtrasField = MainActivity::class.java.getDeclaredField("pendingVoiceSearchExtras")
        pendingVoiceSearchExtrasField.isAccessible = true
        val extras = pendingVoiceSearchExtrasField.get(activity) as? Bundle

        assertNotNull(extras)
        val genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)
        assertEquals(500, genre?.length)
        assertEquals("a".repeat(500), genre)
    }
}
