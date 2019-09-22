package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import kotlin.native.concurrent.SharedImmutable

@SharedImmutable
actual val PACKET_MAX_COPY_SIZE: Int = 200

@SharedImmutable
internal const val BUFFER_VIEW_POOL_SIZE = 1024

@SharedImmutable
internal const val BUFFER_VIEW_SIZE = 4096

actual fun BytePacketBuilder(headerSizeHint: Int) = BytePacketBuilder(headerSizeHint, ChunkBuffer.Pool)

actual typealias EOFException = io.ktor.utils.io.errors.EOFException
