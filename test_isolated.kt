fun main() {
    val lock = Any()
    val _cachedFiles = mutableListOf<String>()
    for (i in 0 until 100000) {
        _cachedFiles.add("file$i")
    }

    val time = kotlin.system.measureTimeMillis {
        synchronized(lock) {
            for (i in _cachedFiles.indices) {
                // simulate copy and update map
                _cachedFiles[i] = _cachedFiles[i] + "_updated"
            }
        }
    }
    println("Original time: $time ms")

    val time2 = kotlin.system.measureTimeMillis {
        val updates = mutableMapOf<Int, String>()
        for (i in _cachedFiles.indices) {
            updates[i] = _cachedFiles[i] + "_updated2"
        }
        synchronized(lock) {
            for ((i, updated) in updates) {
                _cachedFiles[i] = updated
            }
        }
    }
    println("Optimized time: $time2 ms")
}
