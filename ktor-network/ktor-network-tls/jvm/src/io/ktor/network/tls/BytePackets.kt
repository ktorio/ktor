/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*

internal fun interface BytePacketParser<T> {
    suspend fun read(input: ByteReadPacket): T
}

internal fun interface BytePacketWriter<T> {
    fun write(output: BytePacketBuilder, value: T)
}

internal interface BytePacketSerializer<T>: BytePacketParser<T>, BytePacketWriter<T>
