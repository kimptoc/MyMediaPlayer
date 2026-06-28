package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import java.io.InputStream

internal object Mp4CoverExtractor {

    // Upper bound when searching for 'moov' at the file level where the total size is unknown;
    // any real MP4 file is orders of magnitude smaller than this.
    private const val MAX_FILE_SEARCH_BYTES = Long.MAX_VALUE / 2

    // Walks the MP4 atom tree to extract cover art from the iTunes 'covr' box.
    // Handles both moov/meta/ilst and moov/udta/meta/ilst structures.
    fun extractCoverArt(context: Context, uriString: String): ByteArray? {
        if (uriString.isEmpty()) return null
        val input = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
        return input.use { extractCoverArt(it) }
    }

    @VisibleForTesting
    internal fun extractCoverArt(input: InputStream): ByteArray? {
        val moovSize = mp4FindAtom(input, MAX_FILE_SEARCH_BYTES, "moov").takeIf { it >= 0 } ?: return null
        var remaining = moovSize
        while (remaining >= 8) {
            val hdr = mp4ReadHeader(input) ?: return null
            remaining -= 8
            val bodySize = hdr.first - 8
            if (bodySize < 0) return null
            remaining -= bodySize
            when (hdr.second) {
                "meta" -> {
                    mp4SkipExact(input, 4) // full-box version+flags
                    return mp4CoverArtInIlst(input, bodySize - 4)
                }
                "udta" -> {
                    val metaSize = mp4FindAtom(input, bodySize, "meta")
                    if (metaSize >= 0) {
                        mp4SkipExact(input, 4)
                        return mp4CoverArtInIlst(input, metaSize - 4)
                    }
                    return null
                }
                else -> mp4SkipExact(input, bodySize)
            }
        }
        return null
    }

    private fun mp4CoverArtInIlst(stream: InputStream, searchSize: Long): ByteArray? {
        val ilstSize = mp4FindAtom(stream, searchSize, "ilst").takeIf { it >= 0 } ?: return null
        var remaining = ilstSize
        while (remaining >= 8) {
            val hdr = mp4ReadHeader(stream) ?: break
            remaining -= 8
            val bodySize = hdr.first - 8
            if (bodySize < 0) break
            if (hdr.second == "covr") {
                val dataSize = mp4FindAtom(stream, bodySize, "data").takeIf { it >= 0 } ?: return null
                mp4SkipExact(stream, 8) // 4-byte type indicator + 4-byte locale
                val imageSize = (dataSize - 8).toInt()
                if (imageSize <= 0) return null
                return mp4ReadExact(stream, imageSize)
            }
            if (bodySize > remaining) break
            mp4SkipExact(stream, bodySize)
            remaining -= bodySize
        }
        return null
    }

    private fun mp4FindAtom(stream: InputStream, containerSize: Long, target: String): Long {
        var remaining = containerSize
        while (remaining >= 8) {
            val hdr = mp4ReadHeader(stream) ?: return -1
            remaining -= 8
            val bodySize = hdr.first - 8
            if (bodySize < 0) return -1
            if (hdr.second == target) return bodySize
            if (bodySize > remaining) return -1
            mp4SkipExact(stream, bodySize)
            remaining -= bodySize
        }
        return -1
    }

    private fun mp4ReadHeader(stream: InputStream): Pair<Long, String>? {
        val buf = mp4ReadExact(stream, 8) ?: return null
        val size = ((buf[0].toLong() and 0xFF) shl 24) or
                   ((buf[1].toLong() and 0xFF) shl 16) or
                   ((buf[2].toLong() and 0xFF) shl 8) or
                   (buf[3].toLong() and 0xFF)
        val type = String(buf, 4, 4, Charsets.US_ASCII)
        return size to type
    }

    // Uses read() rather than skip() for reliability on SAF-backed streams where skip() may return 0.
    private fun mp4SkipExact(stream: InputStream, n: Long) {
        var remaining = n
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val read = stream.read(buf, 0, toRead)
            if (read < 0) return
            remaining -= read
        }
    }

    private fun mp4ReadExact(stream: InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = stream.read(buf, offset, n - offset)
            if (read < 0) return null
            offset += read
        }
        return buf
    }
}
