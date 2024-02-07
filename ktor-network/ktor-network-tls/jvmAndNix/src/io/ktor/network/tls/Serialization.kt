/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

internal fun interface BytePacketReader<T> {
    suspend fun read(input: ByteReadPacket): T
}

internal fun interface BytePacketWriter<T> {
    fun write(output: BytePacketBuilder, value: T)
}

internal fun interface ByteChannelWriter<T> {
    suspend fun write(output: ByteWriteChannel, value: T)
}

internal fun interface ByteChannelReader<T> {
    suspend fun read(input: ByteReadChannel): T
}

internal interface BytePacketSerializer<T> : BytePacketReader<T>, BytePacketWriter<T>
internal interface ByteChannelSerializer<T> : ByteChannelReader<T>, ByteChannelWriter<T>
