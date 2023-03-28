/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

import io.ktor.network.quic.tls.*
import kotlinx.atomicfu.*

/**
 * This class takes care of handling packet numbers, issuing and acknowledging them.
 */
internal class PacketNumberSpace {
    private val increment = atomic(0L)
    private val largestAcknowledged = atomic(0L)

    private val unacknowledgedLocalPacketNumbers = mutableSetOf<Long>()
    private val unacknowledgedPeerPacketNumbers = mutableSetOf<Long>()

    private val sentAckPackets = mutableMapOf<Long, Set<Long>>()

    val largestPacketNumber: Long get() = increment.getAndAdd(0)

    fun next(): Long {
        return increment.getAndIncrement().apply { unacknowledgedLocalPacketNumbers.add(this) }
    }

    fun receivedPacket(packetNumber: Long) {
        increment.getAndUpdate { maxOf(packetNumber, it) }
        unacknowledgedPeerPacketNumbers.add(packetNumber)
    }

    /**
     * @param ackRanges from [io.ktor.network.quic.frames.FrameProcessor.acceptACK]
     */
    fun processAcknowledgements(ackRanges: LongArray) {
        if (ackRanges.isEmpty()) return // todo error?

        for (i in ackRanges.indices step 2) {
            for (packetNumber in ackRanges[i] downTo ackRanges[i + 1]) {
                unacknowledgedLocalPacketNumbers.remove(packetNumber)
                sentAckPackets.remove(packetNumber)?.let { numbers ->
                    unacknowledgedPeerPacketNumbers.removeAll(numbers)
                }
            }
        }

        largestAcknowledged.updateAndGet { maxOf(ackRanges.first(), it) }
    }

    /**
     * @param packetNumber the number of the packet that will hold this ackRange
     *
     * @return ackRanges to write via [io.ktor.network.quic.frames.FrameWriter.writeACK].
     * Null if everything is acknowledged
     */
    fun getAckRanges(packetNumber: Long): LongArray? {
        if (unacknowledgedPeerPacketNumbers.isEmpty()) return null

        sentAckPackets[packetNumber] = unacknowledgedPeerPacketNumbers.toSet()

        val result = mutableListOf<Long>()
        var last = unacknowledgedPeerPacketNumbers.first()
        result.add(last)
        unacknowledgedPeerPacketNumbers.sortedDescending().forEach {
            if (it != last - 1) {
                result.add(last)
                result.add(it)
            }

            last = it
        }

        return result.toLongArray()
    }

    /**
     * @return set of packet numbers issued locally that had not been acknowledged yet (possibly lost)
     */
    fun getUnacknowledgedLocalPacketNumbers(): Set<Long> {
        return unacknowledgedLocalPacketNumbers
    }

    class Pool {
        private val pool = Array(3) { PacketNumberSpace() }

        operator fun get(level: EncryptionLevel): PacketNumberSpace = pool[level.ordinal]
    }
}
