/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import platform.Foundation.*
import kotlin.coroutines.CoroutineContext

@OptIn(DelicateCoroutinesApi::class)
internal class DarwinTaskHandler(
    private val requestData: HttpRequestData,
    private val callContext: CoroutineContext,
) {
    val response: CompletableDeferred<HttpResponseData> = CompletableDeferred()

    private val requestTime: GMTDate = GMTDate()
    private val bodyChunks = Channel<ByteArray>(Channel.UNLIMITED)

    private var pendingFailure: Throwable? = null
        get() = field?.also { field = null }

    private var metrics: NSURLSessionTaskTransactionMetrics? = null

    private val body: ByteReadChannel = GlobalScope.writer(callContext) {
        try {
            bodyChunks.consumeEach {
                channel.writeFully(it)
                channel.flush()
            }
        } catch (cause: CancellationException) {
            bodyChunks.cancel(cause)
            throw cause
        }
    }.channel

    fun receiveData(dataTask: NSURLSessionDataTask, data: NSData) {
        val content = data.toByteArray()
        try {
            bodyChunks.trySend(content).isSuccess
        } catch (_: CancellationException) {
            dataTask.cancel()
        }
    }

    fun saveFailure(cause: Throwable) {
        pendingFailure = cause
    }

    fun saveMetrics(taskMetrics: NSURLSessionTaskTransactionMetrics) {
        metrics = taskMetrics
    }

    fun complete(task: NSURLSessionTask, didCompleteWithError: NSError?) {
        if (didCompleteWithError != null) {
            val exception = pendingFailure ?: handleNSError(requestData, didCompleteWithError)
            bodyChunks.close(exception)
            response.completeExceptionally(exception)
            return
        }

        if (!response.isCompleted) {
            val result = task.response as NSHTTPURLResponse
            response.complete(result.toResponseData(requestData))
        }

        bodyChunks.close()
    }

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, InternalAPI::class)
    fun NSHTTPURLResponse.toResponseData(requestData: HttpRequestData): HttpResponseData {
        val status = HttpStatusCode.fromValue(statusCode.convert())
        val headers = readHeaders(requestData.method, requestData.attributes)
        val responseBody: Any = requestData.attributes.getOrNull(ResponseAdapterAttributeKey)
            ?.adapt(requestData, status, headers, body, requestData.body, callContext)
            ?: body

        return HttpResponseData(
            status,
            requestTime,
            headers,
            protocolVersion(),
            responseBody,
            callContext
        )
    }

    private fun protocolVersion(): HttpProtocolVersion = when (metrics?.networkProtocolName) {
        "http/1.1" -> HttpProtocolVersion.HTTP_1_1
        "h2", "h2c" -> HttpProtocolVersion.HTTP_2_0
        "h3" -> HttpProtocolVersion.HTTP_3_0
        else -> HttpProtocolVersion.HTTP_1_1
    }
}
