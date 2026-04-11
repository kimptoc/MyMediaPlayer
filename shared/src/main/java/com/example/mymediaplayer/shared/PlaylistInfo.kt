package com.example.mymediaplayer.shared

import java.security.MessageDigest
import android.util.Base64

data class PlaylistInfo(
    val uriString: String,
    val displayName: String
)

/** Deterministic short hash for use in mediaIds (avoids long content:// URIs). */
fun playlistShortId(uriString: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(uriString.toByteArray())
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).take(16)
}
