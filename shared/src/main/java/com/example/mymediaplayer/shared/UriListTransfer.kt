package com.example.mymediaplayer.shared

/**
 * Sends a list of song URIs from the phone to the service in parts small enough for one
 * Bundle. A big list used to be swapped for one random song (search) or a browse ID that
 * often meant the whole library (#518 points 1 and 2); now the service gets the exact list.
 */
object UriListTransfer {
    const val EXTRA_TRANSFER_ID = "transfer_id"
    const val EXTRA_PART_INDEX = "part_index"
    const val EXTRA_PART_COUNT = "part_count"
    const val MAX_URIS_PER_PART = 500

    fun split(uris: List<String>, partSize: Int = MAX_URIS_PER_PART): List<List<String>> =
        if (uris.isEmpty()) listOf(emptyList()) else uris.chunked(partSize)
}

/**
 * Collects the parts of one transfer on the service side. Only the latest transfer counts:
 * part 0 starts a new one, and parts from any other transfer are dropped. The full list is
 * returned only once every part has arrived in order, so playback never starts on part of it.
 */
class UriListAssembler {
    private var transferId: String? = null
    private var expectedParts = 0
    private var nextPart = 0
    private val uris = ArrayList<String>()

    fun accept(transferId: String, partIndex: Int, partCount: Int, part: List<String>): List<String>? {
        if (partCount <= 0 || partIndex !in 0 until partCount) return null
        if (partIndex == 0) {
            this.transferId = transferId
            expectedParts = partCount
            nextPart = 0
            uris.clear()
        }
        if (transferId != this.transferId || partCount != expectedParts || partIndex != nextPart) {
            // A part went missing or belongs to an older transfer: wait for a new part 0.
            if (transferId == this.transferId) reset()
            return null
        }
        uris.addAll(part)
        nextPart++
        if (nextPart < expectedParts) return null
        val complete = ArrayList(uris)
        reset()
        return complete
    }

    private fun reset() {
        transferId = null
        expectedParts = 0
        nextPart = 0
        uris.clear()
    }
}
