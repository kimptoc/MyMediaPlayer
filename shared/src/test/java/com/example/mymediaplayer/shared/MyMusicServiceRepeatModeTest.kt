package com.example.mymediaplayer.shared

import android.os.Looper
import android.support.v4.media.session.MediaControllerCompat
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
    private lateinit var controller: MediaControllerCompat

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
        controller = MediaControllerCompat(service, service.sessionToken!!)
    }

    @Test
    fun shuffleUiList_withRepeatOne_turnsRepeatOff() {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        playUiList(shuffle = true)

        assertRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
    }

    @Test
    fun playAllUiList_withRepeatOne_turnsRepeatOff() {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        playUiList(shuffle = false)

        assertRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
    }

    @Test
    fun shuffleUiList_withRepeatAll_keepsRepeatAll() {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)

        playUiList(shuffle = true)

        assertRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)
    }

    @Test
    fun smartPlaylist_withRepeatOne_turnsRepeatOff() = runBlocking {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        service.handlePlaySmartPlaylistForTesting("recently_added")
        idle()

        assertRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
    }

    @Test
    fun tappingSongThatStartsNewQueue_withRepeatOne_turnsRepeatOff() = runBlocking {
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        service.handlePlaySingleItemForTesting(songs[1].uriString)
        idle()

        assertEquals(songs.size, service.currentPlaylistQueue().size)
        assertRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
    }

    @Test
    fun replayingCurrentSong_withRepeatOne_keepsRepeatOne() = runBlocking {
        service.handlePlaySingleItemForTesting(songs[1].uriString)
        idle()
        setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

        // e.g. a Bluetooth reconnect re-sending the current track keeps the existing queue
        service.handlePlaySingleItemForTesting(service.currentPlaylistQueue()[currentQueueIndex()].uriString)
        idle()

        assertRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
    }

    private fun setRepeatMode(mode: Int) {
        service.setRepeatModeForTesting(mode)
        idle()
        assertRepeatMode(mode)
    }

    /** Checks the mode controllers (phone, Android Auto) see and the one restored next session. */
    private fun assertRepeatMode(expected: Int) {
        assertEquals("session repeat mode", expected, controller.repeatMode)
        val saved = MyMusicService.getPrefs(service).getInt("resume_repeat_mode", -1)
        assertEquals("saved repeat mode", expected, saved)
    }

    /** The service path behind the phone's Play/Shuffle buttons (ACTION_PLAY_UI_LIST). */
    private fun playUiList(shuffle: Boolean) {
        service.playProvidedUriListForTesting(songs.map { it.uriString }, shuffle, "Test list")
        idle()
    }

    private fun currentQueueIndex(): Int {
        val field = MyMusicService::class.java.getDeclaredField("currentQueueIndex")
        field.isAccessible = true
        return field.getInt(service)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
