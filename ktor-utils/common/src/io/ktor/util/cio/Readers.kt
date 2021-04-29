/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.cio

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.contracts.*

/**
 * Convert [ByteReadChannel] to [ByteArray]
 */
public suspend fun ByteReadChannel.toByteArray(limit: Int = Int.MAX_VALUE): ByteArray =
    readRemaining(limit.toLong()).readBytes()

/**
 * Executes [block] on [ByteWriteChannel] and close it down correctly whether an exception
 */
public inline fun ByteWriteChannel.use(block: ByteWriteChannel.() -> Unit) {
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
