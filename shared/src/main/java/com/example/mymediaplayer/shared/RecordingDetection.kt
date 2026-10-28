package com.example.mymediaplayer.shared

import java.net.URLDecoder
import java.util.Locale

// Folders that recorder apps save into: Samsung's Voice Recorder ("Recordings", "Voice Recorder")
// and iPhone Voice Memos copied across ("Sounds"). Matched as whole folder names only, so
// "Pet Sounds" or a file named "Recordings.mp3" is still music.
private val RECORDING_FOLDERS = setOf("sounds", "recordings", "voice recorder")

/**
 * True when [pathOrUri] has a recorder folder among its folders. Takes a MediaStore relative
 * path ("Recordings/Voice Recorder/") or a SAF document URI, whose path is percent-encoded.
 */
fun isRecordingMedia(pathOrUri: String?): Boolean {
    if (pathOrUri.isNullOrBlank()) return false
    val decoded = if (pathOrUri.contains('%')) {
        runCatching { URLDecoder.decode(pathOrUri.replace("+", "%2B"), "UTF-8") }.getOrDefault(pathOrUri)
    } else {
        pathOrUri
    }
    val segments = decoded.split('/', ':')
    // The last segment is the file name, or empty for a folder path ending in '/'.
    for (i in 0 until segments.size - 1) {
        if (segments[i].trim().lowercase(Locale.US) in RECORDING_FOLDERS) return true
    }
    return false
}
