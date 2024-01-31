/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.recovery

internal class Recovery(private val retransmission: Retransmission) {
    private val retransmissionLog = hashMapOf<Long, MutableList<suspend Retransmission.() -> Unit>>()

    fun registerForRecovery(packetNumber: Long, retransmit: suspend Retransmission.() -> Unit) {
        retransmissionLog.getOrPut(packetNumber) { mutableListOf() }.add(retransmit)
    }

    fun packetAcknowledged(packetNumber: Long) {
        retransmissionLog.remove(packetNumber)
    }

    suspend fun retransmitPacket(packetNumber: Long) {
        val retransmissions = retransmissionLog.remove(packetNumber) ?: return

        retransmissions.forEach { handler ->
            retransmission.handler()
        }
    }
}
