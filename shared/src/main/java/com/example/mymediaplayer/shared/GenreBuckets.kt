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
        normalized.contains("j-pop") || normalized.contains("jpop") ||
            normalized.contains("k-pop") || normalized.contains("kpop") ||
            normalized.contains("synthpop") || normalized.contains("electropop") ||
            normalized.contains("pop") || normalized.contains("dance pop") -> "Pop"
        normalized.contains("metalcore") || normalized.contains("hardcore") ||
            normalized.contains("nu metal") || normalized.contains("progressive metal") ||
            normalized.contains("death metal") || normalized.contains("black metal") ||
            normalized.contains("metal") ||
            normalized.contains("alternative rock") || normalized.contains("alt rock") ||
            normalized.contains("indie rock") || normalized.contains("hard rock") ||
            normalized.contains("classic rock") || normalized.contains("punk") ||
            normalized.contains("grunge") || normalized.contains("rock") -> "Rock/Metal"
        normalized.contains("hip hop") || normalized.contains("hip-hop") ||
            normalized.contains(" rap") || normalized.startsWith("rap") ||
            normalized.contains("trap") -> "Hip-Hop/Rap"
        normalized.contains("r&b") || normalized.contains("rnb") ||
            normalized.contains("rhythm and blues") || normalized.contains("soul") ||
            normalized.contains("motown") || normalized.contains("neo soul") ||
            normalized.contains("funk") || normalized.contains("gospel") ||
            normalized.contains("christian") || normalized.contains("worship") -> "R&B/Soul"
        normalized.contains("electronic") || normalized.contains("edm") ||
            normalized.contains("house") || normalized.contains("techno") ||
            normalized.contains("trance") || normalized.contains("dubstep") ||
            normalized.contains("dnb") || normalized.contains("drum and bass") ||
            normalized.contains("electro") || normalized.contains("ambient") ||
            normalized.contains("breakbeat") || normalized.contains("garage") -> "Electronic/Dance"
        normalized.contains("country") || normalized.contains("bluegrass") ||
            normalized.contains("folk") || normalized.contains("americana") ||
            normalized.contains("singer-songwriter") || normalized.contains("acoustic") ->
            "Country/Folk"
        normalized.contains("jazz") || normalized.contains("blues") ||
            normalized.contains("swing") || normalized.contains("big band") ||
            normalized.contains("bebop") || normalized.contains("fusion") -> "Jazz/Blues"
        normalized.contains("classical") || normalized.contains("orchestra") ||
            normalized.contains("baroque") || normalized.contains("opera") -> "Classical"
        normalized.contains("latin") || normalized.contains("reggaeton") ||
            normalized.contains("salsa") || normalized.contains("bachata") -> "Latin"
        normalized.contains("reggae") || normalized.contains("dancehall") ||
            normalized.contains("ska") || normalized.contains("afrobeat") -> "Latin"
        normalized.contains("soundtrack") || normalized.contains("score") ||
            normalized.contains("ost") || normalized.contains("musical") ||
            normalized.contains("spoken") || normalized.contains("podcast") ||
            normalized.contains("audiobook") -> "Other"
        else -> "Other"
    }
}
