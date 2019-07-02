/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.cio

import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.nio.*
import kotlin.contracts.*

/**
 * Convert [ByteReadChannel] to [ByteArray]
 */
suspend fun ByteReadChannel.toByteArray(limit: Int = Int.MAX_VALUE): ByteArray =
    readRemaining(limit.toLong()).readBytes()

/**
 * Read data chunks from [ByteReadChannel] using buffer
 */
@InternalAPI
suspend inline fun ByteReadChannel.pass(buffer: ByteBuffer, block: (ByteBuffer) -> Unit) {
    while (!isClosedForRead) {
        buffer.clear()
        readAvailable(buffer)

        buffer.flip()
        block(buffer)
    }
}

/**
 * Executes [block] on [ByteWriteChannel] and close it down correctly whether an exception
 */
inline fun ByteWriteChannel.use(block: ByteWriteChannel.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    try {
        block()
    } catch (cause: Throwable) {
        close(cause)
        throw cause
    } finally {
        close()
    }
}
