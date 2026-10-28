package com.example.mymediaplayer.shared

import java.net.URLDecoder
import java.util.Locale

// Folders that recorder apps save into: Samsung's Voice Recorder ("Recordings", "Voice Recorder")
// and iPhone Voice Memos copied across ("Sounds"). Matched as whole folder names only, so
// "Pet Sounds" or a file named "Recordings.mp3" is still music.
private val RECORDING_FOLDERS = setOf("sounds", "recordings", "voice recorder")

// Android/media/<package>/ holds an app's own media: WhatsApp and Telegram audio messages,
// voice notes and forwarded clips. Not part of the music library.
private const val APP_MEDIA_PARENT = "android"
private const val APP_MEDIA_FOLDER = "media"

/**
 * True when [pathOrUri] has a recorder folder among its folders, or is under an app's
 * Android/media folder. Takes a MediaStore relative
 * path ("Recordings/Voice Recorder/") or a SAF document URI, whose path is percent-encoded.
 */
fun isRecordingMedia(pathOrUri: String?): Boolean {
    if (pathOrUri.isNullOrBlank()) return false
    val decoded = if (pathOrUri.contains('%')) {
        runCatching { URLDecoder.decode(pathOrUri.replace("+", "%2B"), "UTF-8") }.getOrDefault(pathOrUri)
    } else {
        pathOrUri
    }
    val segments = decoded.split('/', ':').map { it.trim().lowercase(Locale.US) }
    // The last segment is the file name, or empty for a folder path ending in '/'.
    val lastFolder = segments.size - 2
    for (i in 0..lastFolder) {
        if (segments[i] in RECORDING_FOLDERS) return true
        if (segments[i] == APP_MEDIA_PARENT && i < lastFolder && segments[i + 1] == APP_MEDIA_FOLDER) {
            return true
        }
    }
    return false
}
