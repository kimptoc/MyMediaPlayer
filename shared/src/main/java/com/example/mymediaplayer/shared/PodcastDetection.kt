package com.example.mymediaplayer.shared

import java.util.Locale

fun isPodcastMedia(rawGenre: String?, pathOrUri: String?): Boolean {
    val genre = rawGenre.orEmpty().lowercase(Locale.US)
    if (genre.contains("podcast") || genre.contains("audiobook") || genre.contains("spoken")) {
        return true
    }
    val path = pathOrUri.orEmpty().lowercase(Locale.US)
    return path.contains("podcast")
}
