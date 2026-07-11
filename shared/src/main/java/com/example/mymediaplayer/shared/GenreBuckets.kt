package com.example.mymediaplayer.shared

import java.util.Locale

const val PODCAST_GENRE: String = "Podcasts"

private val WHITESPACE_REGEX = Regex("\\s+")

private data class GenreRule(
    val name: String,
    val contains: List<String> = emptyList(),
    val startsWith: List<String> = emptyList(),
    val exact: List<String> = emptyList()
) {
    fun matches(s: String): Boolean {
        for (i in exact.indices) {
            if (s == exact[i]) return true
        }
        for (i in startsWith.indices) {
            if (s.startsWith(startsWith[i])) return true
        }
        for (i in contains.indices) {
            if (s.contains(contains[i])) return true
        }
        return false
    }
}

private val GENRE_RULES = arrayOf(
    GenreRule("Pop", contains = listOf("j-pop", "jpop", "k-pop", "kpop", "synthpop", "electropop", "pop", "dance pop")),
    GenreRule("Rock/Metal", contains = listOf("metalcore", "hardcore", "nu metal", "progressive metal", "death metal", "black metal", "metal", "alternative rock", "alt rock", "indie rock", "hard rock", "classic rock", "punk", "grunge", "rock")),
    GenreRule("Hip-Hop/Rap", contains = listOf("hip hop", "hip-hop", " rap", "trap"), startsWith = listOf("rap")),
    GenreRule("R&B/Soul", contains = listOf("r&b", "rnb", "rhythm and blues", "soul", "motown", "neo soul", "funk", "gospel", "christian", "worship")),
    GenreRule("Electronic/Dance", contains = listOf("electronic", "edm", "house", "techno", "trance", "dubstep", "dnb", "drum and bass", "electro", "ambient", "breakbeat", "garage")),
    GenreRule("Country/Folk", contains = listOf("country", "bluegrass", "folk", "americana", "singer-songwriter", "acoustic")),
    GenreRule("Jazz/Blues", contains = listOf("jazz", "blues", "swing", "big band", "bebop", "fusion")),
    GenreRule("Classical", contains = listOf("classical", "orchestra", "baroque", "opera")),
    GenreRule("Latin", contains = listOf("latin", "reggaeton", "salsa", "bachata", "afrobeat")),
    GenreRule("Reggae", contains = listOf("reggae", "dancehall", "ska"), exact = listOf("dub")),
    GenreRule("Other", contains = listOf("soundtrack", "score", "ost", "musical", "spoken", "podcast", "audiobook"))
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

    for (i in GENRE_RULES.indices) {
        if (GENRE_RULES[i].matches(normalized)) {
            return GENRE_RULES[i].name
        }
    }
    return "Other"
}
