/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import kotlin.coroutines.*
import kotlin.time.*

/**
 * Setup [HttpURLConnection] timeout configuration using [HttpTimeout.HttpTimeoutCapabilityConfiguration] as a source.
 */
@OptIn(InternalAPI::class)
internal fun HttpURLConnection.setupTimeoutAttributes(requestData: HttpRequestData) {
    requestData.getCapabilityOrNull(HttpTimeout)?.let { timeoutAttributes ->
        timeoutAttributes.connectTimeout?.let { connectTimeout = convertTimeoutToIntWithInfiniteAsZero(it) }
        timeoutAttributes.socketTimeout?.let { readTimeout = convertTimeoutToIntWithInfiniteAsZero(it) }
        setupRequestTimeoutAttributes(timeoutAttributes)
    }
}

/**
 * Update [HttpURLConnection] timeout configuration to support request timeout. Required to support blocking
 * [HttpURLConnection.connect] call.
 */
@OptIn(InternalAPI::class)
private fun HttpURLConnection.setupRequestTimeoutAttributes(
    timeoutAttributes: HttpTimeout.HttpTimeoutCapabilityConfiguration
) {
    // Android performs blocking connect call, so we need to add an upper bound on the call time.
    timeoutAttributes.requestTimeout?.let { requestTimeout ->
        if (requestTimeout == Duration.INFINITE) return@let
        if (connectTimeout == 0 || connectTimeout > requestTimeout.inWholeMilliseconds) {
            connectTimeout = convertTimeoutToIntWithInfiniteAsZero(requestTimeout)
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

/**
 * Establish connection and return correspondent [ByteReadChannel].
 */
@OptIn(InternalAPI::class)
internal fun HttpURLConnection.content(callContext: CoroutineContext, request: HttpRequestData): ByteReadChannel = try {
    inputStream?.buffered()
} catch (_: IOException) {
    errorStream?.buffered()
}?.toByteReadChannel(
    context = callContext,
    pool = KtorDefaultPool
)?.let { CoroutineScope(callContext).mapEngineExceptions(it, request) } ?: ByteReadChannel.Empty

/**
 * Checks the exception and identifies timeout exception by it.
 */
private fun Throwable.isTimeoutException(): Boolean =
    this is java.net.SocketTimeoutException || (this is ConnectException && message?.contains("timed out") ?: false)
