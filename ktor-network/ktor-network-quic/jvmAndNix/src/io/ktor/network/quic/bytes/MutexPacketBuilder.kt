/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.bytes

import io.ktor.utils.io.core.*
import kotlinx.coroutines.sync.*

internal class MutexPacketBuilder {
    private val builder = BytePacketBuilder()
    private val lock = Mutex()

    val size get() = builder.size

    suspend fun <T> withLock(body: suspend (BytePacketBuilder) -> T) = lock.withLock {
        body(builder)
    }

    suspend fun flush(body: (BytePacketBuilder) -> Unit = {}): ByteReadPacket = lock.withLock {
        flushNonBlocking(body)
    }

    fun flushNonBlocking(body: (BytePacketBuilder) -> Unit = {}): ByteReadPacket {
        body(builder)

        return builder.build().also { builder.reset() }
    }
}
