package com.example.mymediaplayer.shared

fun MediaCacheService.normalizeGenreForTest(raw: String?): String {
    val method = MediaCacheService::class.java.getDeclaredMethod("normalizeGenre", String::class.java)
    method.isAccessible = true
    return method.invoke(this, raw) as String
}
