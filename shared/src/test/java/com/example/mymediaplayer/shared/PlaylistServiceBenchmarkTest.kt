package com.example.mymediaplayer.shared

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@RunWith(RobolectricTestRunner::class)
class PlaylistServiceBenchmarkTest {

    // Extracted logic to benchmark parallelization
    class DummyDoc(val isDir: Boolean, val name: String, val children: List<DummyDoc> = emptyList()) {
        fun listFiles(): List<DummyDoc> {
            Thread.sleep(10) // Simulate IPC latency
            return children
        }
    }

    @Test
    fun testTraversalPerformance() = runBlocking {
        // Create tree: Root -> 50 dirs -> each has 5 files
        val dirs = (1..50).map { i ->
            val files = (1..5).map { j -> DummyDoc(false, "file_${i}_${j}.m3u") }
            DummyDoc(true, "dir_$i", files)
        }
        val root = DummyDoc(true, "root", dirs)

        val seqTime = measureTimeMillis {
            val out = mutableListOf<DummyDoc>()
            val stack = ArrayDeque<DummyDoc>()
            stack.add(root)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                val children = runCatching { node.listFiles() }.getOrNull() ?: continue
                for (child in children) {
                    if (child.isDir) {
                        stack.add(child)
                        continue
                    }
                    if (child.name.endsWith(".m3u")) {
                        out.add(child)
                    }
                }
            }
            assert(out.size == 250)
        }

        val asyncTime = measureTimeMillis {
            val out = mutableListOf<DummyDoc>()
            val mutex = Mutex()

            suspend fun traverse(node: DummyDoc) {
                val children = runCatching { node.listFiles() }.getOrNull() ?: return

                val dirs = mutableListOf<DummyDoc>()
                for (child in children) {
                    if (child.isDir) {
                        dirs.add(child)
                    } else if (child.name.endsWith(".m3u")) {
                        mutex.withLock { out.add(child) }
                    }
                }

                dirs.map { dir ->
                    async(Dispatchers.IO) {
                        traverse(dir)
                    }
                }.awaitAll()
            }

            traverse(root)
            assert(out.size == 250)
        }

        println("Sequential took: $seqTime ms")
        println("Parallel took: $asyncTime ms")
        assert(asyncTime < seqTime) { "Parallel should be faster!" }
    }
}
