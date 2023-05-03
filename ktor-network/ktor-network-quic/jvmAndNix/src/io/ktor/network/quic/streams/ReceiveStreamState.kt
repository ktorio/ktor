/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.streams

import io.ktor.utils.io.core.*

internal class ReceiveStreamState(
    private val onDataReceived: suspend (orderedData: ByteReadPacket) -> Unit,
    private val onClose: () -> Unit,
) {
    private var state: ReceiveState = ReceiveState.Recv

    private val knownOffsets: HashMap<Long, ByteArray> = hashMapOf()

    private val unknownOffsets: HashSet<Long> = hashSetOf()

    private var size = -1L

    suspend fun receive(data: ByteArray, offset: Long, fin: Boolean) {
        if (state == ReceiveState.Closed) return

        if (fin) {
            state = ReceiveState.SizeKnown
            size = offset + data.size
        } else if (offset + data.size != size) {
            unknownOffsets.add(offset + data.size)
        }

        if (knownOffsets.containsKey(offset)) return

        knownOffsets[offset] = data

        unknownOffsets.remove(offset)

        val sorted = knownOffsets.entries.sortedBy { it.key }
        var expectedOffset = sorted.firstOrNull()?.key ?: return

        val orderedData = buildPacket {
            var i = 0
            while (i < sorted.size) {
                val (chunkOffset, chunk) = sorted[i]

                if (expectedOffset != chunkOffset) {
                    break
                }

                writeFully(chunk)
                knownOffsets.remove(chunkOffset)

                expectedOffset = chunkOffset + chunk.size

                i++
            }
        }

        if (orderedData.remaining != 0L) {
            onDataReceived(orderedData)
        }

        if (state == ReceiveState.SizeKnown && unknownOffsets.isEmpty()) {
            state = ReceiveState.Closed
            onClose()
        }
    }
}

private enum class ReceiveState {
    Recv, SizeKnown, Closed
}
