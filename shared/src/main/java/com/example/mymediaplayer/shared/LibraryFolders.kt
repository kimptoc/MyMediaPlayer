package com.example.mymediaplayer.shared

import android.os.Build
import android.provider.MediaStore
import java.util.Locale

// Android's folders for audio that isn't part of the library: system sounds, and the Recordings
// folder, which holds call recordings and voicemail. MediaStore files audio in these folders as
// not music, so the whole-device scan never sees it; the folder scan skips them to match (#530).
private val NON_LIBRARY_FOLDERS = setOf("alarms", "notifications", "ringtones", "recordings")

/** True when the folder scan should not look inside a folder called [name]. */
fun isNonLibraryFolder(name: String?): Boolean =
    name != null && name.trim().lowercase(Locale.US) in NON_LIBRARY_FOLDERS

/**
 * Which MediaStore audio the whole-device scan reads: music, plus podcasts and audiobooks, which
 * MediaStore files outside music by folder (Podcasts/, Audiobooks/). The folder scan lists those
 * under the Podcasts genre, so the whole-device scan does too.
 */
fun wholeDeviceAudioSelection(sdkInt: Int = Build.VERSION.SDK_INT): String {
    val kinds = mutableListOf(MediaStore.Audio.Media.IS_MUSIC, MediaStore.Audio.Media.IS_PODCAST)
    if (sdkInt >= Build.VERSION_CODES.Q) kinds += MediaStore.Audio.Media.IS_AUDIOBOOK
    return kinds.joinToString(" OR ") { "$it != 0" }
}
