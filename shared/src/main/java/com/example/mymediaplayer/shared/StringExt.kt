package com.example.mymediaplayer.shared

import java.util.Locale

/**
 * Returns the file extension of the string representation of a filename,
 * including the dot (e.g., ".mp3"), or "(none)" if no extension is found.
 */
fun String.fileExtension(): String {
    val ext = this.substringAfterLast('.', "")
    return if (ext.isNotEmpty()) ".$ext".lowercase(Locale.US) else "(none)"
}
