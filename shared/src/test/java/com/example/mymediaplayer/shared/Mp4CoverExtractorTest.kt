package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class Mp4CoverExtractorTest {
    @Test
    fun extractCoverArt_withStandardMoovMetaIlstCovr_returnsImageBytes() {
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val mp4 = atom("moov", meta(ilst(covr(imageBytes))))

        val result = Mp4CoverExtractor.extractCoverArt(ByteArrayInputStream(mp4))

        assertNotNull(result)
        assertArrayEquals(imageBytes, result!!)
    }

    @Test
    fun extractCoverArt_withUdtaMetaIlstCovr_returnsImageBytes() {
        val imageBytes = byteArrayOf(5, 6, 7)
        val mp4 = atom("moov", atom("udta", meta(ilst(covr(imageBytes)))))

        val result = Mp4CoverExtractor.extractCoverArt(ByteArrayInputStream(mp4))

        assertNotNull(result)
        assertArrayEquals(imageBytes, result!!)
    }

    @Test
    fun extractCoverArt_withContentResolverStream_returnsImageBytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/song.m4a")
        val imageBytes = byteArrayOf(8, 9, 10)
        val mp4 = atom("moov", meta(ilst(covr(imageBytes))))
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(mp4)
        }

        val result = Mp4CoverExtractor.extractCoverArt(context, uri.toString())

        assertNotNull(result)
        assertArrayEquals(imageBytes, result!!)
    }

    @Test
    fun extractCoverArt_withoutCovrAtom_returnsNull() {
        val mp4 = atom("moov", meta(ilst(atom("free", byteArrayOf(1, 2)))))

        val result = Mp4CoverExtractor.extractCoverArt(ByteArrayInputStream(mp4))

        assertNull(result)
    }

    @Test
    fun extractCoverArt_whenAtomSizeExceedsRemainingBytes_returnsNull() {
        val malformed = intBytes(32) + ascii("moov") + byteArrayOf(1, 2)

        val result = Mp4CoverExtractor.extractCoverArt(ByteArrayInputStream(malformed))

        assertNull(result)
    }

    @Test
    fun extractCoverArt_whenAtomBodySizeIsNegative_returnsNull() {
        val malformed = intBytes(4) + ascii("moov")

        val result = Mp4CoverExtractor.extractCoverArt(ByteArrayInputStream(malformed))

        assertNull(result)
    }

    @Test
    fun extractCoverArt_withEmptyStream_returnsNull() {
        val result = Mp4CoverExtractor.extractCoverArt(ByteArrayInputStream(byteArrayOf()))

        assertNull(result)
    }

    @Test
    fun extractCoverArt_withTruncatedAtomHeader_returnsNull() {
        val result = Mp4CoverExtractor.extractCoverArt(ByteArrayInputStream(byteArrayOf(0, 0, 0)))

        assertNull(result)
    }

    @Test
    fun extractCoverArt_whenCovrDataImageSizeIsZero_returnsNull() {
        val emptyImageData = atom("data", byteArrayOf(0, 0, 0, 13, 0, 0, 0, 0))
        val mp4 = atom("moov", meta(ilst(atom("covr", emptyImageData))))

        val result = Mp4CoverExtractor.extractCoverArt(ByteArrayInputStream(mp4))

        assertNull(result)
    }

    private fun meta(vararg children: ByteArray): ByteArray {
        return atom("meta", byteArrayOf(0, 0, 0, 0) + concat(*children))
    }

    private fun ilst(vararg children: ByteArray): ByteArray {
        return atom("ilst", concat(*children))
    }

    private fun covr(imageBytes: ByteArray): ByteArray {
        return atom("covr", data(imageBytes))
    }

    private fun data(imageBytes: ByteArray): ByteArray {
        return atom("data", byteArrayOf(0, 0, 0, 13, 0, 0, 0, 0) + imageBytes)
    }

    private fun atom(type: String, body: ByteArray): ByteArray {
        return intBytes(body.size + 8) + ascii(type) + body
    }

    private fun intBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )
    }

    private fun ascii(value: String): ByteArray {
        return value.toByteArray(StandardCharsets.ISO_8859_1).copyOf(4)
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val out = ByteArray(arrays.sumOf { it.size })
        var offset = 0
        for (array in arrays) {
            array.copyInto(out, offset)
            offset += array.size
        }
        return out
    }
}
