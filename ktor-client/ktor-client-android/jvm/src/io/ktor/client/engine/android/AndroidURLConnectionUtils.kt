/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.*
import kotlin.coroutines.CoroutineContext

/**
 * Setup [HttpURLConnection] timeout configuration using [HttpTimeoutConfig] as a source.
 */
@OptIn(InternalAPI::class)
internal fun HttpURLConnection.setupTimeoutAttributes(requestData: HttpRequestData) {
    requestData.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeoutAttributes ->
        timeoutAttributes.connectTimeoutMillis?.let { connectTimeout = convertLongTimeoutToIntWithInfiniteAsZero(it) }
        timeoutAttributes.socketTimeoutMillis?.let { readTimeout = convertLongTimeoutToIntWithInfiniteAsZero(it) }
        setupRequestTimeoutAttributes(timeoutAttributes)
    }
}

/**
 * Update [HttpURLConnection] timeout configuration to support request timeout. Required to support blocking
 * [HttpURLConnection.connect] call.
 */
@OptIn(InternalAPI::class)
private fun HttpURLConnection.setupRequestTimeoutAttributes(
    timeoutAttributes: HttpTimeoutConfig
) {
    // Android performs blocking connect call, so we need to add an upper bound on the call time.
    timeoutAttributes.requestTimeoutMillis?.let { requestTimeout ->
        if (requestTimeout == HttpTimeoutConfig.INFINITE_TIMEOUT_MS) return@let
        if (connectTimeout == 0 || connectTimeout > requestTimeout) {
            connectTimeout = convertLongTimeoutToIntWithInfiniteAsZero(requestTimeout)
        }
    }
}

/**
 * Executes [block] catching [java.net.SocketTimeoutException] and returning [SocketTimeoutException] instead
 * of it. If request timeout happens earlier [HttpRequestTimeoutException] will be thrown.
 */
internal suspend fun <T> HttpURLConnection.timeoutAwareConnection(
    request: HttpRequestData,
    block: (HttpURLConnection) -> T
): T {
    try {
        return block(this)
    } catch (cause: Throwable) {
        // Allow to throw request timeout cancellation exception instead of connect timeout exception if needed.
        yield()
        throw when {
            cause.isTimeoutException() -> ConnectTimeoutException(request, cause)
            else -> cause
        }
    }
}

@OptIn(InternalCoroutinesApi::class)
internal fun HttpURLConnection.disconnectOnCancellation(callContext: CoroutineContext) {
    callContext.job.invokeOnCompletion(onCancelling = true) { cause ->
        if (cause != null) disconnect()
    }
}

/** Adapts the response stream, preventing physical closure during an active read. */
internal fun HttpURLConnection.content(status: Int, callContext: CoroutineContext): ByteReadChannel {
    if (status in listOf(HttpStatusCode.NotModified.value, HttpStatusCode.NoContent.value)) {
        return ByteReadChannel.Empty
    }

    val stream = try {
        inputStream?.buffered()
    } catch (_: IOException) {
        errorStream?.buffered()
    } ?: return ByteReadChannel.Empty

    val responseStream = AndroidResponseInputStream(stream, callContext.job)
    return responseStream.toByteReadChannel(context = callContext, pool = KtorDefaultPool).also {
        responseStream.channel = it
    }
}

/**
 * Prevents closure from overlapping a read of an Android response stream.
 * Closing such a stream may drain unread data and must not overlap an active read.
 */
internal class AndroidResponseInputStream(
    stream: InputStream,
    private val callJob: Job
) : FilterInputStream(stream) {
    private val readLock = Any()

    // Parent cancellation may trigger close before the channel is assigned.
    @Volatile
    var channel: ByteReadChannel? = null

    override fun read(): Int = synchronized(readLock) {
        super.read()
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = synchronized(readLock) {
        super.read(bytes, offset, length)
    }

    override fun close() {
        val cause = channel?.closedCause
        if (cause != null) {
            val cancellation = cause as? CancellationException
                ?: CancellationException("Response body was cancelled", cause)
            // Request connection abort before taking the lock held by an active read.
            callJob.cancel(cancellation)
        }

        synchronized(readLock) {
            runCatching { super.close() }
        }
    }
}

/**
 * Checks the exception and identifies timeout exception by it.
 */
private fun Throwable.isTimeoutException(): Boolean =
    this is java.net.SocketTimeoutException || (this is ConnectException && message?.contains("timed out") ?: false)
