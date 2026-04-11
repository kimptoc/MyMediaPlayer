import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.system.measureTimeMillis

fun main() {
    val data = "A".repeat(10000).toByteArray()

    val timeWithoutUse = measureTimeMillis {
        for (i in 1..100000) {
            val stream: InputStream = ByteArrayInputStream(data)
            val text = stream.bufferedReader().readText()
            // stream is not closed
        }
    }

    val timeWithUse = measureTimeMillis {
        for (i in 1..100000) {
            val stream: InputStream = ByteArrayInputStream(data)
            val text = stream.bufferedReader().use { it.readText() }
        }
    }

    println("Time without use: $timeWithoutUse ms")
    println("Time with use: $timeWithUse ms")
}
