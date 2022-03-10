/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*

public interface LogWriter {
    public suspend fun write(request: HttpRequestBuilder): OutgoingContent?
    public suspend fun write(request: HttpRequest): OutgoingContent?
    public fun write(response: HttpResponse)
    public fun write(request: HttpRequestBuilder, cause: Throwable)
    public fun write(response: HttpResponse, cause: Throwable)
    public suspend fun logResponseBody(response: HttpResponse)
}

@OptIn(InternalAPI::class)
internal class SimpleLogWriter(
    private val logger: Logger,
    private val level: LogLevel,
) : LogWriter {
    override fun write(response: HttpResponse) {
        if (level.info) {
            logger.log("RESPONSE: ${response.status}")
            logger.log("METHOD: ${response.call.request.method}")
            logger.log("FROM: ${response.call.request.url}")
        }

        if (level.headers) {
            logger.log("COMMON HEADERS")
            logHeaders(response.headers.entries())
        }
    }

    override suspend fun write(request: HttpRequestBuilder): OutgoingContent? {
        if (level.info) {
            logger.log("REQUEST: ${Url(request.url)}")
            logger.log("METHOD: ${request.method}")
        }

        val content = request.body as OutgoingContent

        if (level.headers) {
            logger.log("COMMON HEADERS")
            logHeaders(request.headers.entries())

            logger.log("CONTENT HEADERS")
            content.contentLength?.let { logger.logHeader(HttpHeaders.ContentLength, it.toString()) }
            content.contentType?.let { logger.logHeader(HttpHeaders.ContentType, it.toString()) }
            logHeaders(content.headers.entries())
        }

        return if (level.body) {
            logRequestBody(content)
        } else null
    }

    override suspend fun write(request: HttpRequest): OutgoingContent? {
        if (level.info) {
            logger.log("REQUEST: ${request.url}")
            logger.log("METHOD: ${request.method}")
        }

        val content = request.content

        if (level.headers) {
            logger.log("COMMON HEADERS")
            logHeaders(request.headers.entries())

            logger.log("CONTENT HEADERS")
            content.contentLength?.let { logger.logHeader(HttpHeaders.ContentLength, it.toString()) }
            content.contentType?.let { logger.logHeader(HttpHeaders.ContentType, it.toString()) }
            logHeaders(content.headers.entries())
        }

        return if (level.body) {
            logRequestBody(content)
        } else null
    }

    override suspend fun logResponseBody(response: HttpResponse): Unit = with(logger) {
        val contentType = response.contentType()
        log("BODY Content-Type: $contentType")
        log("BODY START")
        val message = response.content.tryReadText(contentType?.charset() ?: Charsets.UTF_8)
            ?: "[response body omitted]"
        log(message)
        log("BODY END")
    }

    override fun write(request: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            logger.log("REQUEST ${Url(request.url)} failed with exception: $cause")
        }
    }

    override fun write(response: HttpResponse, cause: Throwable) {
        if (level.info) {
            logger.log("RESPONSE ${response.call.request.url} failed with exception: $cause")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun logRequestBody(content: OutgoingContent): OutgoingContent {
        logger.log("BODY Content-Type: ${content.contentType}")

        val charset = content.contentType?.charset() ?: Charsets.UTF_8

        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Unconfined) {
            val text = channel.tryReadText(charset) ?: "[request body omitted]"
            logger.log("BODY START")
            logger.log(text)
            logger.log("BODY END")
        }

        return content.observe(channel)
    }

    private fun logHeaders(headers: Set<Map.Entry<String, List<String>>>) {
        val sortedHeaders = headers.toList().sortedBy { it.key }

        sortedHeaders.forEach { (key, values) ->
            logger.logHeader(key, values.joinToString("; "))
        }
    }

    private fun Logger.logHeader(key: String, value: String) {
        log("-> $key: $value")
    }
}
