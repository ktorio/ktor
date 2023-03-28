/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.bytes

import io.ktor.utils.io.core.*
import kotlinx.atomicfu.locks.*

internal class LockablePacketBuilder {
    private var builder = BytePacketBuilder()
    private val lock = reentrantLock()

    val size get() = builder.size

    inline fun <T> withLock(body: (BytePacketBuilder) -> T) = lock.withLock {
        body(builder)
    }

    fun flush(body: (BytePacketBuilder) -> Unit = {}): ByteReadPacket = lock.withLock {
        body(builder)

        builder.build().also { builder = BytePacketBuilder() }
    }
}
