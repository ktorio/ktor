/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.*

@Suppress("ACTUAL_WITHOUT_EXPECT")
actual class HttpConnectTimeoutException actual constructor(request: HttpRequestData) :
    ConnectException(
        "Connect timeout has been expired [url=${request.url}, connect_timeout=${request.getCapabilityOrNull(
            HttpTimeout
        )?.connectTimeoutMillis ?: "unknown"} ms]"
    )

@Suppress("ACTUAL_WITHOUT_EXPECT")
actual class HttpSocketTimeoutException actual constructor(request: HttpRequestData) :
    SocketTimeoutException(
        "Socket timeout has been expired [url=${request.url}, socket_timeout=${request.getCapabilityOrNull(
            HttpTimeout
        )?.socketTimeoutMillis ?: "unknown"}] ms"
    )

/**
 * Returns [ByteReadChannel] with [ByteChannel.close] handler that returns [HttpSocketTimeoutException] instead of
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
 * Returns [ByteWriteChannel] with [ByteChannel.close] handler that returns [HttpSocketTimeoutException] instead of
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
 * Creates [ByteChannel] that maps close exceptions (close the channel with [HttpSocketTimeoutException] if asked to
 * close it with [SocketTimeoutException]).
 */
private fun ByteChannelWithMappedExceptions(request: HttpRequestData) = ByteChannel { cause ->
    when (cause?.rootCause) {
        is SocketTimeoutException -> HttpSocketTimeoutException(request)
        else -> cause
    }
}
