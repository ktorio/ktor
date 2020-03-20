/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException

/**
 * Install default transformers.
 * Usually installed by default so there is no need to use it
 * unless you have disabled it via [HttpClientConfig.useDefaultTransformers].
 */
fun HttpClient.defaultTransformers() {
    requestPipeline.intercept(HttpRequestPipeline.Render) { body ->
        if (context.headers[HttpHeaders.Accept] == null) {
            context.headers.append(HttpHeaders.Accept, "*/*")
        }

        val contentType = context.headers[HttpHeaders.ContentType]?.let {
            ContentType.parse(it)
        }

        val content = when (body) {
            is String -> {
                TextContent(body, contentType ?: ContentType.Text.Plain)
            }
            is ByteArray -> object : OutgoingContent.ByteArrayContent() {
                override val contentType: ContentType = contentType ?: ContentType.Application.OctetStream
                override val contentLength: Long = body.size.toLong()
                override fun bytes(): ByteArray = body
            }
            is ByteReadChannel -> object : OutgoingContent.ReadChannelContent() {
                override val contentType: ContentType = contentType ?: ContentType.Application.OctetStream
                override fun readFrom(): ByteReadChannel = body
            }
            else -> null
        }

        if (content != null) {
            context.headers.remove(HttpHeaders.ContentType)
            proceedWith(content)
        }
    }

    responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, body) ->
        if (body !is ByteReadChannel) return@intercept
        val response = context.response
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLong() ?: Long.MAX_VALUE

        when (info.type) {
            Unit::class -> {
                body.cancel()
                proceedWith(HttpResponseContainer(info, Unit))
            }
            Int::class -> {
                proceedWith(HttpResponseContainer(info, body.readRemaining().readText().toInt()))
            }
            ByteReadPacket::class,
            Input::class -> {
                proceedWith(HttpResponseContainer(info, body.readRemaining()))
            }
            ByteArray::class -> {
                val readRemaining = body.readRemaining(contentLength)
                if (contentLength < Long.MAX_VALUE) {
                    check(readRemaining.remaining == contentLength) {
                        "Expected $contentLength, actual ${readRemaining.remaining}"
                    }
                }

                proceedWith(HttpResponseContainer(info, readRemaining.readBytes()))
            }
            ByteReadChannel::class -> {
                val channel: ByteReadChannel = writer {
                    try {
                        body.copyTo(channel, limit = Long.MAX_VALUE)
                    } catch (cause: CancellationException) {
                        response.cancel(cause)
                        throw cause
                    } catch (cause: Throwable) {
                        response.cancel("Receive failed", cause)
                        throw cause
                    } finally {
                        response.complete()
                    }
                }.channel

                proceedWith(HttpResponseContainer(info, channel))
            }
            HttpStatusCode::class -> {
                body.cancel()
                proceedWith(HttpResponseContainer(info, response.status))
            }
        }
    }

    platformDefaultTransformers()
}

internal expect fun HttpClient.platformDefaultTransformers()
