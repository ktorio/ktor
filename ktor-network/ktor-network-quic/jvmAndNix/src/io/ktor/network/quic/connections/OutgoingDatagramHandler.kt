/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

import io.ktor.network.quic.bytes.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.*

internal class OutgoingDatagramHandler(
    private val outgoingChannel: SendChannel<Datagram>,
    private val getAddress: () -> SocketAddress,
) {
    private val buffer = LockablePacketBuilder()

    val usedSize: Int get() = buffer.size

    suspend fun write(body: suspend (BytePacketBuilder) -> Unit) = buffer.withLock {
        body(it)
    }

    suspend fun flush() {
        println("OutgoingDatagramHandler: flush, used size: $usedSize")
        val datagram = Datagram(buffer.flush(), getAddress())
        outgoingChannel.send(datagram)
    }
}
