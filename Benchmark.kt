import java.util.ArrayList
import kotlin.system.measureTimeMillis

data class MediaFileInfo(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val durationMs: Long?,
    val year: Int?,
    val addedAtMs: Long?
)

fun main() {
    val count = 100000
    val uris = ArrayList<String>(count)
    val names = ArrayList<String>(count)
    val sizes = LongArray(count)
    val titles: ArrayList<String>? = ArrayList<String>(count)
    val artists: ArrayList<String>? = ArrayList<String>(count)
    val albums: ArrayList<String>? = ArrayList<String>(count)
    val genres: ArrayList<String>? = ArrayList<String>(count)
    val durations: LongArray? = LongArray(count)
    val years: IntArray? = IntArray(count)
    val addedAt: LongArray? = LongArray(count)

    for (i in 0 until count) {
        uris.add("content://media/external/audio/media/$i")
        names.add("Song$i.mp3")
        sizes[i] = 1000L + i
        titles!!.add("Title $i")
        artists!!.add("Artist $i")
        albums!!.add("Album $i")
        genres!!.add("Genre $i")
        durations!![i] = 120000L + i
        years!![i] = 2000 + (i % 20)
        addedAt!![i] = 1600000000000L + i
    }

    // Warm up
    for (iter in 0 until 10) {
        val mappedFiles = ArrayList<MediaFileInfo>(count)
        for (i in 0 until count) {
            val title = titles?.getOrNull(i).orEmpty().ifBlank { names[i].substringBeforeLast('.') }
            val artist = artists?.getOrNull(i).orEmpty().ifBlank { null }
            val album = albums?.getOrNull(i).orEmpty().ifBlank { null }
            val genre = genres?.getOrNull(i).orEmpty().ifBlank { null }
            val durationMs = durations?.getOrNull(i)?.takeIf { it >= 0L }
            val year = years?.getOrNull(i)?.takeIf { it > 0 }
            val addedAtMs = addedAt?.getOrNull(i)?.takeIf { it >= 0L }
            mappedFiles.add(
                MediaFileInfo(
                    uriString = uris[i],
                    displayName = names[i],
                    sizeBytes = sizes[i],
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    durationMs = durationMs,
                    year = year,
                    addedAtMs = addedAtMs
                )
            )
        }
    }

    // Benchmark Manual Iteration
    var totalTimeManual = 0L
    for (iter in 0 until 50) {
        totalTimeManual += measureTimeMillis {
            val mappedFiles = ArrayList<MediaFileInfo>(count)
            for (i in 0 until count) {
                val title = titles?.getOrNull(i).orEmpty().ifBlank { names[i].substringBeforeLast('.') }
                val artist = artists?.getOrNull(i).orEmpty().ifBlank { null }
                val album = albums?.getOrNull(i).orEmpty().ifBlank { null }
                val genre = genres?.getOrNull(i).orEmpty().ifBlank { null }
                val durationMs = durations?.getOrNull(i)?.takeIf { it >= 0L }
                val year = years?.getOrNull(i)?.takeIf { it > 0 }
                val addedAtMs = addedAt?.getOrNull(i)?.takeIf { it >= 0L }
                mappedFiles.add(
                    MediaFileInfo(
                        uriString = uris[i],
                        displayName = names[i],
                        sizeBytes = sizes[i],
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        durationMs = durationMs,
                        year = year,
                        addedAtMs = addedAtMs
                    )
                )
            }
        }
    }

    println("Manual Iteration Average: ${totalTimeManual / 50.0} ms")

    // Benchmark mapTo
    var totalTimeMapTo = 0L
    for (iter in 0 until 50) {
        totalTimeMapTo += measureTimeMillis {
            val mappedFiles = (0 until count).mapTo(ArrayList<MediaFileInfo>(count)) { i ->
                val title = titles?.getOrNull(i).orEmpty().ifBlank { names[i].substringBeforeLast('.') }
                val artist = artists?.getOrNull(i).orEmpty().ifBlank { null }
                val album = albums?.getOrNull(i).orEmpty().ifBlank { null }
                val genre = genres?.getOrNull(i).orEmpty().ifBlank { null }
                val durationMs = durations?.getOrNull(i)?.takeIf { it >= 0L }
                val year = years?.getOrNull(i)?.takeIf { it > 0 }
                val addedAtMs = addedAt?.getOrNull(i)?.takeIf { it >= 0L }
                MediaFileInfo(
                    uriString = uris[i],
                    displayName = names[i],
                    sizeBytes = sizes[i],
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    durationMs = durationMs,
                    year = year,
                    addedAtMs = addedAtMs
                )
            }
        }
    }

    println("mapTo Average: ${totalTimeMapTo / 50.0} ms")
}
