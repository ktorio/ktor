/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import okhttp3.*
import java.io.*
import java.net.*
import kotlin.coroutines.*
import okhttp3.Headers as OkHttpHeaders

internal suspend fun OkHttpClient.execute(
    request: Request,
    requestData: HttpRequestData
): Response = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)

    call.enqueue(OkHttpCallback(requestData, continuation))

    continuation.invokeOnCancellation {
        call.cancel()
    }
}

private class OkHttpCallback(
    private val requestData: HttpRequestData,
    private val continuation: CancellableContinuation<Response>
) : Callback {
    override fun onFailure(call: Call, e: IOException) {
        if (continuation.isCancelled) {
            return
        }

        continuation.resumeWithException(mapOkHttpException(requestData, e))
    }

    override fun onResponse(call: Call, response: Response) {
        if (!call.isCanceled()) {
            continuation.resume(response)
        }
    }
}

internal fun OkHttpHeaders.fromOkHttp(): Headers = object : Headers {
    override val caseInsensitiveName: Boolean = true

    override fun getAll(name: String): List<String>? = this@fromOkHttp.values(name).takeIf { it.isNotEmpty() }

    override fun names(): Set<String> = this@fromOkHttp.names()

    override fun entries(): Set<Map.Entry<String, List<String>>> = this@fromOkHttp.toMultimap().entries

    override fun isEmpty(): Boolean = this@fromOkHttp.size == 0
}

@Suppress("DEPRECATION")
internal fun Protocol.fromOkHttp(): HttpProtocolVersion = when (this) {
    Protocol.HTTP_1_0 -> HttpProtocolVersion.HTTP_1_0
    Protocol.HTTP_1_1 -> HttpProtocolVersion.HTTP_1_1
    Protocol.SPDY_3 -> HttpProtocolVersion.SPDY_3
    Protocol.HTTP_2 -> HttpProtocolVersion.HTTP_2_0
    Protocol.H2_PRIOR_KNOWLEDGE -> HttpProtocolVersion.HTTP_2_0
    Protocol.QUIC -> HttpProtocolVersion.QUIC
}

private fun mapOkHttpException(
    requestData: HttpRequestData,
    origin: IOException
): Throwable = when (val cause = origin.unwrapSuppressed()) {
    is SocketTimeoutException ->
        if (cause.isConnectException()) {
            ConnectTimeoutException(requestData, cause)
        } else {
            SocketTimeoutException(requestData, cause)
        }
    else -> cause
}

private fun IOException.isConnectException() =
    message?.contains("connect", ignoreCase = true) == true

private fun IOException.unwrapSuppressed(): Throwable {
    if (suppressed.isNotEmpty()) return suppressed[0]
    return this
}
