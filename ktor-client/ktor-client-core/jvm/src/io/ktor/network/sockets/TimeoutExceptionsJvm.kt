/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.*

/**
 * This exception is thrown in case connect timeout exceeded.
 */
@Suppress("ACTUAL_WITHOUT_EXPECT")
actual class ConnectTimeoutException actual constructor(
    message: String, override val cause: Throwable?
) : ConnectException(message) {
}

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
@Suppress("ACTUAL_WITHOUT_EXPECT")
actual class SocketTimeoutException actual constructor(
    message: String, override val cause: Throwable?
) : java.net.SocketTimeoutException(message)

/**
 * Returns [ByteReadChannel] with [ByteChannel.close] handler that returns [SocketTimeoutException] instead of
 * [SocketTimeoutException].
 */
@InternalAPI
fun CoroutineScope.mapEngineExceptions(input: ByteReadChannel, request: HttpRequestData): ByteReadChannel {
    val replacementChannel = ByteChannelWithMappedExceptions(request)

    writer(coroutineContext, replacementChannel) {
        try {
            input.joinTo(replacementChannel, closeOnEnd = true)
        } catch (cause: Throwable) {
            input.cancel(cause)
        }
    }

    return replacementChannel
}

/**
 * Returns [ByteWriteChannel] with [ByteChannel.close] handler that returns [SocketTimeoutException] instead of
 * [SocketTimeoutException].
 */
@InternalAPI
fun CoroutineScope.mapEngineExceptions(input: ByteWriteChannel, request: HttpRequestData): ByteWriteChannel {
    val replacementChannel = ByteChannelWithMappedExceptions(request)

    writer(coroutineContext, replacementChannel) {
        try {
            replacementChannel.joinTo(input, closeOnEnd = true)
        } catch (cause: Throwable) {
            replacementChannel.close(cause)
        }
    }

    return replacementChannel
}

/**
 * Creates [ByteChannel] that maps close exceptions (close the channel with [SocketTimeoutException] if asked to
 * close it with [SocketTimeoutException]).
 */
private fun ByteChannelWithMappedExceptions(request: HttpRequestData) = ByteChannel { cause ->
    when (cause?.rootCause) {
        is java.net.SocketTimeoutException -> SocketTimeoutException(request, cause)
        else -> cause
    }
}
