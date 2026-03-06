package com.example.mymediaplayer.shared

import java.util.Locale

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
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase(Locale.US)

    return when {
        normalized.contains("hip hop") || normalized.contains("hip-hop") ||
            normalized.contains(" rap") || normalized.startsWith("rap") ||
            normalized.contains("trap") -> "Hip-Hop/Rap"
        normalized.contains("r&b") || normalized.contains("rnb") ||
            normalized.contains("rhythm and blues") || normalized.contains("soul") ||
            normalized.contains("motown") -> "R&B/Soul"
        normalized.contains("electronic") || normalized.contains("edm") ||
            normalized.contains("house") || normalized.contains("techno") ||
            normalized.contains("trance") || normalized.contains("dubstep") ||
            normalized.contains("dnb") || normalized.contains("drum and bass") -> "Electronic"
        normalized.contains("soundtrack") || normalized.contains("score") ||
            normalized.contains("ost") || normalized.contains("musical") -> "Soundtrack"
        normalized.contains("classical") || normalized.contains("orchestra") ||
            normalized.contains("baroque") || normalized.contains("opera") -> "Classical"
        normalized.contains("blues") -> "Blues"
        normalized.contains("jazz") -> "Jazz"
        normalized.contains("metal") -> "Metal"
        normalized.contains("rock") || normalized.contains("grunge") ||
            normalized.contains("punk") -> "Rock"
        normalized.contains("country") || normalized.contains("bluegrass") -> "Country"
        normalized.contains("folk") || normalized.contains("americana") -> "Folk"
        normalized.contains("latin") || normalized.contains("reggaeton") ||
            normalized.contains("salsa") || normalized.contains("bachata") -> "Latin"
        normalized.contains("reggae") || normalized.contains("dancehall") ||
            normalized.contains("ska") -> "Reggae/Ska"
        normalized.contains("gospel") || normalized.contains("christian") ||
            normalized.contains("worship") -> "Gospel/Christian"
        normalized.contains("k-pop") || normalized.contains("kpop") ||
            normalized.contains("pop") -> "Pop"
        normalized.contains("spoken") || normalized.contains("podcast") ||
            normalized.contains("audiobook") -> "Spoken"
        else -> primary
    }
}
