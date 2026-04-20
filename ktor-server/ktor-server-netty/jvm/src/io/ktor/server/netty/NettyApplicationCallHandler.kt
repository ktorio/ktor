/*
* Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.http1.*
import io.ktor.utils.io.*
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import kotlinx.coroutines.CoroutineName

private const val CHUNKED_VALUE = "chunked"

/**
 * Contains shared constants and helper functions for Netty call handling.
 */
internal object NettyApplicationCallHandler {
    internal val CallHandlerCoroutineName = CoroutineName("call-handler")
}

internal fun NettyHttp1ApplicationRequest.isValid(): Boolean {
    if (httpRequest.decoderResult().isFailure) {
        return false
    }

    if (!headers.contains(HttpHeaders.TransferEncoding)) return true

    val encodings = headers.getAll(HttpHeaders.TransferEncoding) ?: return true
    return encodings.hasValidTransferEncoding()
}

internal fun ChannelHandlerContext.respond408RequestTimeoutHttp1() {
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT)
    response.headers().add(HttpHeaders.ContentLength, "0")
    response.headers().add(HttpHeaders.Connection, "close")
    writeAndFlush(response)
    close()
}

internal suspend fun NettyHttp1ApplicationCall.respondError400BadRequest() {
    logCause()

    val causeMessage = failureCause?.message?.toByteArray(charset = Charsets.UTF_8)
    val content = if (causeMessage != null) ByteReadChannel(causeMessage) else ByteReadChannel.Empty
    val contentLength = causeMessage?.size ?: 0

    response.status(HttpStatusCode.BadRequest)
    response.headers.append(HttpHeaders.ContentLength, contentLength.toString(), safeOnly = false)
    if (contentLength > 0) {
        response.headers.append(HttpHeaders.ContentType, "text/plain; charset=utf-8", safeOnly = false)
    }
    response.headers.append(HttpHeaders.Connection, "close", safeOnly = false)
    response.sendResponse(chunked = false, content)
    finish()
}

private fun NettyHttp1ApplicationCall.logCause() {
    if (application.log.isTraceEnabled) {
        val cause = failureCause ?: return
        application.log.trace("Failed to decode request", cause)
    }
}

private val NettyHttp1ApplicationCall.failureCause: Throwable?
    get() = httpRequest.decoderResult()?.cause()

internal fun List<String>.hasValidTransferEncoding(): Boolean {
    forEachIndexed { headerIndex, header ->
        val chunkedStart = header.indexOf(CHUNKED_VALUE)
        if (chunkedStart == -1) return@forEachIndexed

        if (chunkedStart > 0 && !header[chunkedStart - 1].isSeparator()) {
            return@forEachIndexed
        }

        val afterChunked: Int = chunkedStart + CHUNKED_VALUE.length
        if (afterChunked < header.length && !header[afterChunked].isSeparator()) {
            return@forEachIndexed
        }

        if (headerIndex != lastIndex) {
            return false
        }

        val chunkedIsNotLast = chunkedStart + CHUNKED_VALUE.length < header.length
        if (chunkedIsNotLast) {
            return false
        }
    }

    return true
}

private fun Char.isSeparator(): Boolean = (this == ' ' || this == ',')
