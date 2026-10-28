package com.example.mymediaplayer.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingDetectionTest {

    @Test
    fun mediaStoreRelativePaths_inRecorderFolders_match() {
        assertTrue(isRecordingMedia("iPhoneData/Sounds/"))
        assertTrue(isRecordingMedia("Recordings/Voice Recorder/"))
        assertTrue(isRecordingMedia("Recordings/Call/"))
        assertTrue(isRecordingMedia("Music/voice recorder/"))
    }

    @Test
    fun safDocumentUris_areDecodedBeforeMatching() {
        assertTrue(
            isRecordingMedia(
                "content://com.android.externalstorage.documents/tree/primary%3AiPhoneData/document/" +
                    "primary%3AiPhoneData%2FSounds%2FNew%20Recording%203.m4a"
            )
        )
        assertTrue(
            isRecordingMedia(
                "content://com.android.externalstorage.documents/tree/primary%3A/document/" +
                    "primary%3ARecordings%2FVoice%20Recorder%2Fmemo.m4a"
            )
        )
    }

    @Test
    fun folderNamesThatOnlyContainTheWord_doNotMatch() {
        assertFalse(isRecordingMedia("Music/Beach Boys/Pet Sounds/"))
        assertFalse(isRecordingMedia("Music/Live Recordings 1999/"))
    }

    @Test
    fun aFileNamedLikeAFolder_doesNotMatch() {
        assertFalse(isRecordingMedia("Music/Band/Recordings.mp3"))
        assertFalse(
            isRecordingMedia(
                "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/" +
                    "primary%3AMusic%2FSounds.mp3"
            )
        )
    }

    @Test
    fun mediaStoreUrisAndEmptyInput_doNotMatch() {
        assertFalse(isRecordingMedia("content://media/external/audio/media/147046"))
        assertFalse(isRecordingMedia(null))
        assertFalse(isRecordingMedia(""))
        assertFalse(isRecordingMedia("Music/Artist/Album/"))
    }
}
