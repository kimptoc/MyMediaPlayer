package com.example.mymediaplayer

import com.example.mymediaplayer.shared.PlaylistInfo
import org.junit.Test
import kotlin.system.measureTimeMillis

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
}
