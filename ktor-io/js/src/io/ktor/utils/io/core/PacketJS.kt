package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*

public actual val PACKET_MAX_COPY_SIZE: Int = 200
internal const val BUFFER_VIEW_POOL_SIZE = 100
internal const val BUFFER_VIEW_SIZE = 4096

public actual fun BytePacketBuilder(headerSizeHint: Int): BytePacketBuilder =
    BytePacketBuilder(headerSizeHint, ChunkBuffer.Pool)

public actual typealias EOFException = io.ktor.utils.io.errors.EOFException

