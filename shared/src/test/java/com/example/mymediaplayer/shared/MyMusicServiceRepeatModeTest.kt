package com.example.mymediaplayer.shared

import android.os.Looper
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Issue #519: Repeat One is saved between sessions and can be set from the car without the
 * phone showing it, so starting a fresh shuffle or Play All must not inherit it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyMusicServiceRepeatModeTest {

    private lateinit var service: MyMusicService

    private val songs = (1..3).map {
        MediaFileInfo(
            uriString = "content://test/song$it",
            displayName = "song$it.mp3",
            sizeBytes = 1L,
            title = "Song $it"
        )
    }

    @Before
    fun setup() {
        EncryptedPrefsManager.clearCacheForTesting()
        MyMusicService.clearPrefsCacheForTesting()
        service = Robolectric.buildService(MyMusicService::class.java).create().get()
        songs.forEach { service.mediaCacheService.addFile(it) }
    }

    @Test
    fun shuffleUiList_withRepeatOne_turnsRepeatOff() {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        playUiList(shuffle = true)

        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE, repeatMode())
    }

    @Test
    fun playAllUiList_withRepeatOne_turnsRepeatOff() {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        playUiList(shuffle = false)

        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE, repeatMode())
    }

    @Test
    fun shuffleUiList_withRepeatAll_keepsRepeatAll() {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)

        playUiList(shuffle = true)

        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL, repeatMode())
    }

    @Test
    fun playingSingleSong_withRepeatOne_keepsRepeatOne() = runBlocking {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        service.handlePlaySingleItemForTesting(songs[1].uriString)
        idle()

        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE, repeatMode())
    }

    private fun setRepeatMode(mode: Int) {
        val field = MyMusicService::class.java.getDeclaredField("repeatMode")
        field.isAccessible = true
        field.setInt(service, mode)
    }

    /** The service path behind the phone's Play/Shuffle buttons (ACTION_PLAY_UI_LIST). */
    private fun playUiList(shuffle: Boolean) {
        val method = MyMusicService::class.java.getDeclaredMethod(
            "playProvidedUriList",
            List::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(service, songs.map { it.uriString }, shuffle, "Test list", false)
        idle()
    }

    private fun repeatMode(): Int {
        val field = MyMusicService::class.java.getDeclaredField("repeatMode")
        field.isAccessible = true
        return field.getInt(service)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
