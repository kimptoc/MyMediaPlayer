package com.example.mymediaplayer.shared

import org.junit.Test
import kotlin.system.measureTimeMillis

class MediaCacheServiceBenchmarkTest {
    @Test
    fun benchmarkAddFileVsAddAllFiles() {
        val service = MediaCacheService()
        val files = List(50000) { i ->
            MediaFileInfo(
                uriString = "content://media/external/audio/media/$i",
                displayName = "Song $i.mp3",
                sizeBytes = 1000L,
                title = "Song $i",
                artist = "Artist $i",
                album = "Album $i"
            )
        }

        // Warmup
        service.clearCache()
        for (i in 0..10) {
            files.take(100).forEach { service.addFile(it) }
        }
        service.clearCache()

        val time1 = measureTimeMillis {
            files.forEach { service.addFile(it) }
        }
        println("addFile individually (50k items): $time1 ms")

        service.clearCache()

        val time2 = measureTimeMillis {
            service.addAllFiles(files)
        }
        println("addAllFiles (50k items): $time2 ms")
    }

    private fun inferGenreFromPathOld(pathLike: String?): String? {
        val normalized = pathLike
            ?.replace('\\', '/')
            ?.lowercase(java.util.Locale.US)
            ?.trim()
            .orEmpty()
        if (normalized.isBlank()) return null
        return when {
            normalized.contains("hip hop") || normalized.contains("hip-hop") ||
                normalized.contains("/rap") || normalized.contains("trap") -> "Hip-Hop"
            normalized.contains("r&b") || normalized.contains("rnb") ||
                normalized.contains("soul") || normalized.contains("motown") -> "R&B"
            normalized.contains("electronic") || normalized.contains("edm") ||
                normalized.contains("house") || normalized.contains("techno") ||
                normalized.contains("trance") || normalized.contains("dubstep") -> "Electronic"
            normalized.contains("rock") || normalized.contains("metal") ||
                normalized.contains("punk") || normalized.contains("grunge") -> "Rock"
            normalized.contains("country") || normalized.contains("bluegrass") -> "Country"
            normalized.contains("folk") || normalized.contains("americana") -> "Folk"
            normalized.contains("classical") || normalized.contains("orchestra") ||
                normalized.contains("opera") || normalized.contains("baroque") -> "Classical"
            normalized.contains("jazz") -> "Jazz"
            normalized.contains("blues") -> "Blues"
            normalized.contains("latin") || normalized.contains("reggaeton") ||
                normalized.contains("salsa") || normalized.contains("bachata") -> "Latin"
            normalized.contains("pop") -> "Pop"
            else -> null
        }
    }

    @Test
    fun benchmarkInferGenreFromPath() {
        val service = MediaCacheService()
        val testPaths = listOf(
            "content://media/external/audio/media/hip hop/song.mp3",
            "content://media/external/audio/media/rock/metal/grunge.mp3",
            "content://media/external/audio/media/classical/orchestra/opera.mp3",
            "content://media/external/audio/media/pop/reggaeton/salsa.mp3",
            "content://media/external/audio/media/unknown_path/other.mp3",
            "C:\\Users\\Music\\Hip-Hop\\song.mp3",
            "C:\\Users\\Music\\Rap\\song.mp3",
            "   /rap/trap/trap_song.mp3   ",
            null,
            "",
            "   "
        )

        // Equivalence check and warmup
        for (path in testPaths) {
            val oldResult = inferGenreFromPathOld(path)
            val newResult = service.inferGenreFromPath(path)
            org.junit.Assert.assertEquals("Inconsistent result for path '$path'", oldResult, newResult)
        }

        repeat(10000) {
            for (path in testPaths) {
                inferGenreFromPathOld(path)
                service.inferGenreFromPath(path)
            }
        }

        val iterations = 200000

        val timeOld = measureTimeMillis {
            repeat(iterations) {
                for (path in testPaths) {
                    inferGenreFromPathOld(path)
                }
            }
        }

        val timeNew = measureTimeMillis {
            repeat(iterations) {
                for (path in testPaths) {
                    service.inferGenreFromPath(path)
                }
            }
        }

        println("inferGenreFromPathOld ($iterations * ${testPaths.size} paths): $timeOld ms")
        println("inferGenreFromPathNew ($iterations * ${testPaths.size} paths): $timeNew ms")
    }
}
