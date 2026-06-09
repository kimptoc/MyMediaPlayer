package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object Mp4CoverExtractor {
    fun extractCoverArt(context: Context, uriString: String): ByteArray? {
        return try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { inputStream ->
                extractCoverArt(inputStream)
            }
        } catch (_: Exception) {
            null
        }
    }

    @VisibleForTesting
    internal fun extractCoverArt(inputStream: InputStream): ByteArray? {
        return try {
            val bytes = inputStream.readBytes()
            findCoverArt(bytes, 0, bytes.size, SearchState.Root)
        } catch (_: IOException) {
            null
        }
    }

    private enum class SearchState {
        Root,
        Moov,
        Udta,
        Meta,
        Ilst,
        Covr
    }

    private data class Atom(
        val type: String,
        val bodyStart: Int,
        val bodyEnd: Int
    )

    private fun findCoverArt(
        bytes: ByteArray,
        start: Int,
        end: Int,
        state: SearchState
    ): ByteArray? {
        if (start < 0 || end > bytes.size || start > end) return null
        var offset = start
        while (offset < end) {
            val atom = readAtom(bytes, offset, end) ?: return null
            val result = when (state) {
                SearchState.Root -> {
                    if (atom.type == "moov") {
                        findCoverArt(bytes, atom.bodyStart, atom.bodyEnd, SearchState.Moov)
                    } else {
                        null
                    }
                }
                SearchState.Moov -> {
                    when (atom.type) {
                        "meta" -> findCoverArtInMeta(bytes, atom)
                        "udta" -> findCoverArt(bytes, atom.bodyStart, atom.bodyEnd, SearchState.Udta)
                        else -> null
                    }
                }
                SearchState.Udta -> {
                    if (atom.type == "meta") {
                        findCoverArtInMeta(bytes, atom)
                    } else {
                        null
                    }
                }
                SearchState.Meta -> {
                    if (atom.type == "ilst") {
                        findCoverArt(bytes, atom.bodyStart, atom.bodyEnd, SearchState.Ilst)
                    } else {
                        null
                    }
                }
                SearchState.Ilst -> {
                    if (atom.type == "covr") {
                        findCoverArt(bytes, atom.bodyStart, atom.bodyEnd, SearchState.Covr)
                    } else {
                        null
                    }
                }
                SearchState.Covr -> {
                    if (atom.type == "data") {
                        extractImageData(bytes, atom)
                    } else {
                        null
                    }
                }
            }
            if (result != null) return result
            offset = atom.bodyEnd
        }
        return null
    }

    private fun findCoverArtInMeta(bytes: ByteArray, atom: Atom): ByteArray? {
        val childrenStart = atom.bodyStart + META_FULL_BOX_HEADER_SIZE
        if (childrenStart > atom.bodyEnd) return null
        return findCoverArt(bytes, childrenStart, atom.bodyEnd, SearchState.Meta)
    }

    private fun extractImageData(bytes: ByteArray, atom: Atom): ByteArray? {
        val imageStart = atom.bodyStart + DATA_HEADER_SIZE
        if (imageStart > atom.bodyEnd) return null
        val imageSize = atom.bodyEnd - imageStart
        if (imageSize <= 0) return null
        return bytes.copyOfRange(imageStart, atom.bodyEnd)
    }

    private fun readAtom(bytes: ByteArray, offset: Int, parentEnd: Int): Atom? {
        if (offset + ATOM_HEADER_SIZE > parentEnd) return null
        val size = readUInt32(bytes, offset) ?: return null
        val type = String(bytes, offset + 4, 4, StandardCharsets.US_ASCII)
        val headerSize: Int
        val atomSize: Long
        if (size == EXTENDED_SIZE_MARKER) {
            if (offset + EXTENDED_ATOM_HEADER_SIZE > parentEnd) return null
            atomSize = readUInt64(bytes, offset + ATOM_HEADER_SIZE) ?: return null
            headerSize = EXTENDED_ATOM_HEADER_SIZE
        } else if (size == TO_END_SIZE_MARKER) {
            atomSize = (parentEnd - offset).toLong()
            headerSize = ATOM_HEADER_SIZE
        } else {
            atomSize = size
            headerSize = ATOM_HEADER_SIZE
        }
        val bodySize = atomSize - headerSize
        if (bodySize < 0) return null
        if (atomSize > Int.MAX_VALUE.toLong()) return null
        if (atomSize > (parentEnd - offset).toLong()) return null
        val atomEnd = offset + atomSize.toInt()
        return Atom(
            type = type,
            bodyStart = offset + headerSize,
            bodyEnd = atomEnd
        )
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long? {
        if (offset + 4 > bytes.size) return null
        return ((bytes[offset].toLong() and BYTE_MASK) shl 24) or
            ((bytes[offset + 1].toLong() and BYTE_MASK) shl 16) or
            ((bytes[offset + 2].toLong() and BYTE_MASK) shl 8) or
            (bytes[offset + 3].toLong() and BYTE_MASK)
    }

    private fun readUInt64(bytes: ByteArray, offset: Int): Long? {
        if (offset + 8 > bytes.size) return null
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and BYTE_MASK)
        }
        return value.takeIf { it >= 0 }
    }

    private const val ATOM_HEADER_SIZE = 8
    private const val EXTENDED_ATOM_HEADER_SIZE = 16
    private const val META_FULL_BOX_HEADER_SIZE = 4
    private const val DATA_HEADER_SIZE = 8
    private const val EXTENDED_SIZE_MARKER = 1L
    private const val TO_END_SIZE_MARKER = 0L
    private const val BYTE_MASK = 0xffL
}
