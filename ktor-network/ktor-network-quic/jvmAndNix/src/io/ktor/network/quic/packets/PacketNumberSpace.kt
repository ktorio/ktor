/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

import io.ktor.network.quic.tls.*
import io.ktor.network.quic.util.*
import kotlinx.atomicfu.*

/**
 * This class takes care of handling packet numbers, issuing and acknowledging them.
 * It handles both local and peer packet numbers
 */
internal class PacketNumberSpace {
    private val logger = logger()
    private val increment = atomic(0L)
    private val _largestAcknowledged = atomic(-1L) // -1 for no acknowledgements yet

    private val unacknowledgedLocalPacketNumbers = mutableSetOf<Long>()
    private val unacknowledgedPeerPacketNumbers = mutableSetOf<Long>()

    private val sentAckPackets = mutableMapOf<Long, List<Long>>()

    val largestPacketNumber: Long get() = increment.getAndAdd(0)
    val largestAcknowledged: Long get() = _largestAcknowledged.getAndAdd(0)

    fun next(): Long {
        return increment.getAndIncrement().apply { unacknowledgedLocalPacketNumbers.add(this) }
    }

    fun receivePacket(packetNumber: Long) {
        unacknowledgedPeerPacketNumbers.add(packetNumber)
    }

    /**
     * @param ackRanges from [io.ktor.network.quic.frames.FrameProcessor.acceptACK]
     */
    fun processAcknowledgements(ackRanges: List<Long>) {
        if (ackRanges.isEmpty()) return // todo error?

        ackRanges.rangesToNumbers().forEach { packetNumber ->
            unacknowledgedLocalPacketNumbers.remove(packetNumber)
            sentAckPackets.remove(packetNumber)?.let { ranges ->
                unacknowledgedPeerPacketNumbers.removeAll(ranges.rangesToNumbers().toSet())
            }
        }

        _largestAcknowledged.updateAndGet { maxOf(ackRanges.first(), it) }
    }

    /**
     * @return pair of `ackRanges` to write via [io.ktor.network.quic.frames.FrameWriter.writeACK]
     * and hook that will remember the number of a packet in which these ranges ere sent.
     *
     * Null if everything is acknowledged
     */
    fun getAckRanges(): List<Long>? {
        if (unacknowledgedPeerPacketNumbers.isEmpty()) return null

        val sorted = unacknowledgedPeerPacketNumbers.sortedDescending()
        val result = mutableListOf<Long>()
        var last = sorted.first()
        result.add(last)
        sorted.forEach {
            if (it != last - 1 && it != last) {
                result.add(last)
                result.add(it)
            }

            last = it
        }
        result.add(last)

        logger.info("generated ACK ranges array: (${result.joinToString()}) from (${unacknowledgedPeerPacketNumbers.joinToString()})") // ktlint-disable max-line-length argument-list-wrapping

        return result
    }

    fun registerSentRanges(ranges: List<Long>, packetNumber: Long) {
        sentAckPackets[packetNumber]?.let {
            // programmer's error
            error("Each packet should have one ack frame, number: $packetNumber, old numbers: ${ranges.joinToString()}")
        }
        sentAckPackets[packetNumber] = ranges
    }

    /**
     * @return set of packet numbers issued locally that had not been acknowledged yet (possibly lost)
     */
    @Suppress("unused")
    fun getUnacknowledgedLocalPacketNumbers(): Set<Long> {
        return unacknowledgedLocalPacketNumbers
    }

    private fun List<Long>.rangesToNumbers(): List<Long> {
        return chunked(2) { (start, end) -> (start downTo end) }.flatten()
    }

    class Pool {
        private val pool = Array(3) { PacketNumberSpace() }

        operator fun get(level: EncryptionLevel): PacketNumberSpace = pool[level.ordinal]
    }
}
