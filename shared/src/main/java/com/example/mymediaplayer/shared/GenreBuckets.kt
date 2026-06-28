package com.example.mymediaplayer.shared

import java.util.Locale

const val PODCAST_GENRE: String = "Podcasts"

private val WHITESPACE_REGEX = Regex("\\s+")

private data class GenreRule(
    val bucket: String,
    val substrings: List<String> = emptyList(),
    val prefixes: List<String> = emptyList(),
    val exactMatches: List<String> = emptyList()
) {
    fun matches(normalized: String): Boolean {
        if (exactMatches.any { normalized == it }) return true
        if (prefixes.any { normalized.startsWith(it) }) return true
        if (substrings.any { normalized.contains(it) }) return true
        return false
    }
}

private val GENRE_RULES = listOf(
    GenreRule(
        bucket = "Pop",
        substrings = listOf("j-pop", "jpop", "k-pop", "kpop", "synthpop", "electropop", "pop", "dance pop")
    ),
    GenreRule(
        bucket = "Rock/Metal",
        substrings = listOf("metalcore", "hardcore", "nu metal", "progressive metal", "death metal", "black metal", "metal", "alternative rock", "alt rock", "indie rock", "hard rock", "classic rock", "punk", "grunge", "rock")
    ),
    GenreRule(
        bucket = "Hip-Hop/Rap",
        substrings = listOf("hip hop", "hip-hop", " rap", "trap"),
        prefixes = listOf("rap")
    ),
    GenreRule(
        bucket = "R&B/Soul",
        substrings = listOf("r&b", "rnb", "rhythm and blues", "soul", "motown", "neo soul", "funk", "gospel", "christian", "worship")
    ),
    GenreRule(
        bucket = "Electronic/Dance",
        substrings = listOf("electronic", "edm", "house", "techno", "trance", "dubstep", "dnb", "drum and bass", "electro", "ambient", "breakbeat", "garage")
    ),
    GenreRule(
        bucket = "Country/Folk",
        substrings = listOf("country", "bluegrass", "folk", "americana", "singer-songwriter", "acoustic")
    ),
    GenreRule(
        bucket = "Jazz/Blues",
        substrings = listOf("jazz", "blues", "swing", "big band", "bebop", "fusion")
    ),
    GenreRule(
        bucket = "Classical",
        substrings = listOf("classical", "orchestra", "baroque", "opera")
    ),
    GenreRule(
        bucket = "Latin",
        substrings = listOf("latin", "reggaeton", "salsa", "bachata", "afrobeat")
    ),
    GenreRule(
        bucket = "Reggae",
        substrings = listOf("reggae", "dancehall", "ska"),
        exactMatches = listOf("dub")
    ),
    GenreRule(
        bucket = "Other",
        substrings = listOf("soundtrack", "score", "ost", "musical", "spoken", "podcast", "audiobook")
    )
)

fun bucketGenre(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return "Other"

    val primary = trimmed
        .split(';', '/', '|', ',')
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    if (primary.isBlank()) return "Other"

    val normalized = primary
        .replace(WHITESPACE_REGEX, " ")
        .trim()
        .lowercase(Locale.US)

    return GENRE_RULES.firstOrNull { it.matches(normalized) }?.bucket ?: "Other"
}
