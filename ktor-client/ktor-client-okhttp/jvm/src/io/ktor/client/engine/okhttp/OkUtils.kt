/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.Headers
import java.io.*
import kotlin.coroutines.*

internal suspend fun OkHttpClient.execute(
    request: Request, requestData: HttpRequestData
): Response = suspendCancellableCoroutine {
    val call = newCall(request)
    val callback = object : Callback {

        override fun onFailure(call: Call, cause: IOException) {
            if (call.isCanceled()) {
                return
            }

            val mappedException = when (cause) {
                is java.net.SocketTimeoutException -> if (cause.message?.contains("connect") == true) {
                    ConnectTimeoutException(requestData, cause)
                } else {
                    SocketTimeoutException(requestData, cause)
                }
                else -> cause
            }

            it.resumeWithException(mappedException)
        }

        override fun onResponse(call: Call, response: Response) {
            if (!call.isCanceled()) it.resume(response)
        }
    }

    call.enqueue(callback)

    it.invokeOnCancellation {
        call.cancel()
    }
}

internal fun Headers.fromOkHttp(): io.ktor.http.Headers = object : io.ktor.http.Headers {
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
