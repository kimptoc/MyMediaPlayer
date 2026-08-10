package com.example.mymediaplayer

import com.example.mymediaplayer.shared.PlaylistInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureTimeMillis

// Deliberately plain JUnit, no Robolectric: these are pure-JVM microbenchmarks, and
// running them under the Android simulator would invalidate the timings they measure.
// The correctness check against the real production method lives in
// MainActivityBluetoothParsingTest, which needs Robolectric to build an Activity.
class MainViewModelPerformanceTest {

    @Test
    fun benchmarkPlaylistUpdate() {
        val playlists = (1..50000).map {
            PlaylistInfo(
                uriString = "content://media/external/playlist/$it",
                displayName = "Playlist $it"
            )
        }

        val playlistToRename = PlaylistInfo(
            uriString = "content://media/external/playlist/25000",
            displayName = "Playlist 25000"
        )

        val renamed = playlistToRename.copy(displayName = "Renamed Playlist 25000")

        val notFoundPlaylist = PlaylistInfo(
            uriString = "content://media/external/playlist/99999",
            displayName = "Playlist 99999"
        )

        // Warmup
        for (i in 1..50) {
            runOriginalLogic(playlists, playlistToRename, renamed)
            runOptimizedLogicArr(playlists, playlistToRename, renamed)

            runOriginalLogic(playlists, notFoundPlaylist, renamed)
            runOptimizedLogicArr(playlists, notFoundPlaylist, renamed)
        }

        val originalTime = measureTimeMillis {
            for (i in 1..200) {
                runOriginalLogic(playlists, playlistToRename, renamed)
            }
        }
        val optimizedArrTime = measureTimeMillis {
            for (i in 1..200) {
                runOptimizedLogicArr(playlists, playlistToRename, renamed)
            }
        }

        val originalTimeNotFound = measureTimeMillis {
            for (i in 1..200) {
                runOriginalLogic(playlists, notFoundPlaylist, renamed)
            }
        }
        val optimizedArrTimeNotFound = measureTimeMillis {
            for (i in 1..200) {
                runOptimizedLogicArr(playlists, notFoundPlaylist, renamed)
            }
        }

        println("Found - Original Time: $originalTime ms")
        println("Found - Optimized Arr Time: $optimizedArrTime ms")
        println("NotFound - Original Time: $originalTimeNotFound ms")
        println("NotFound - Optimized Arr Time: $optimizedArrTimeNotFound ms")
    }

    private fun runOriginalLogic(
        playlists: List<PlaylistInfo>,
        playlist: PlaylistInfo,
        renamed: PlaylistInfo
    ): List<PlaylistInfo> {
        var replaced = false
        return playlists.map { existing ->
            val isTarget = existing.uriString == playlist.uriString ||
                existing.displayName == playlist.displayName
            if (isTarget) {
                replaced = true
                renamed
            } else {
                existing
            }
        }.let {
            if (replaced) it else {
                it.filterNot { p ->
                    p.displayName == playlist.displayName ||
                        p.displayName.removeSuffix(".m3u") == playlist.displayName.removeSuffix(".m3u")
                } + renamed
            }
        }
    }

    private fun runOptimizedLogicArr(
        playlists: List<PlaylistInfo>,
        playlist: PlaylistInfo,
        renamed: PlaylistInfo
    ): List<PlaylistInfo> {
        var replaced = false
        val pUri = playlist.uriString
        val pName = playlist.displayName
        val updatedPlaylists = ArrayList<PlaylistInfo>(playlists.size + 1)

        for (i in 0 until playlists.size) {
            val existing = playlists[i]
            if (existing.uriString == pUri || existing.displayName == pName) {
                replaced = true
                updatedPlaylists.add(renamed)
            } else {
                updatedPlaylists.add(existing)
            }
        }

        if (!replaced) {
            val pNameNoExt = pName.removeSuffix(".m3u")
            val iterator = updatedPlaylists.iterator()
            while(iterator.hasNext()) {
                val p = iterator.next()
                if (p.displayName == pName || p.displayName.removeSuffix(".m3u") == pNameNoExt) {
                    iterator.remove()
                }
            }
            updatedPlaylists.add(renamed)
        }

        return updatedPlaylists
    }

    @Test
    fun benchmarkBluetoothDeviceParsing() {
        // Generate a mock raw bluetooth devices string (5,000 lines)
        val rawLines = (1..5000).map { i ->
            val mac = String.format("00:11:22:33:%02X:%02X", i / 256, i % 256)
            val name = "Device Name $i"
            "$mac\t$name"
        }
        val raw = rawLines.joinToString("\n")

        // The optimized parser must produce identical results to the original for the
        // exact data this benchmark measures - otherwise the speedup is meaningless.
        assertEquals(runOriginalBluetoothParsing(raw), runOptimizedBluetoothParsing(raw))

        // Warmup
        for (i in 1..20) {
            runOriginalBluetoothParsing(raw)
            runOptimizedBluetoothParsing(raw)
        }

        val originalTime = measureTimeMillis {
            for (i in 1..100) {
                runOriginalBluetoothParsing(raw)
            }
        }

        val optimizedTime = measureTimeMillis {
            for (i in 1..100) {
                runOptimizedBluetoothParsing(raw)
            }
        }

        println("Bluetooth Parsing - Original Time: $originalTime ms")
        println("Bluetooth Parsing - Optimized Time: $optimizedTime ms")
    }

    private fun runOriginalBluetoothParsing(raw: String): Map<String, String?> {
        val decoded = mutableMapOf<String, String?>()
        if (raw.isNotBlank()) {
            raw.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('\t', limit = 2)
                val address = parts[0].trim()
                if (address.isBlank()) return@forEach
                val name = parts.getOrNull(1)?.trim()?.ifBlank { null }
                decoded[address] = name
            }
        }
        return decoded
    }

    private fun trimSubstring(s: String, start: Int, end: Int): String {
        var first = start
        while (first < end && s[first].isWhitespace()) {
            first++
        }
        var last = end
        while (last > first && s[last - 1].isWhitespace()) {
            last--
        }
        if (first >= last) return ""
        return s.substring(first, last)
    }

    private fun runOptimizedBluetoothParsing(raw: String): Map<String, String?> {
        val decoded = mutableMapOf<String, String?>()
        val length = raw.length
        var start = 0
        while (start < length) {
            var nextNewLine = raw.indexOf('\n', start)
            if (nextNewLine == -1) {
                nextNewLine = length
            }
            var tabIndex = -1
            for (i in start until nextNewLine) {
                if (raw[i] == '\t') {
                    tabIndex = i
                    break
                }
            }
            if (tabIndex == -1) {
                val address = trimSubstring(raw, start, nextNewLine)
                if (address.isNotEmpty()) {
                    decoded[address] = null
                }
            } else {
                val address = trimSubstring(raw, start, tabIndex)
                if (address.isNotEmpty()) {
                    val name = trimSubstring(raw, tabIndex + 1, nextNewLine)
                    decoded[address] = if (name.isEmpty()) null else name
                }
            }
            start = nextNewLine + 1
        }
        return decoded
    }
}
