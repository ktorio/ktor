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
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

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

        when (body) {
            is String -> {
                val contentType = context.headers[HttpHeaders.ContentType]?.let {
                    context.headers.remove(HttpHeaders.ContentType)
                    ContentType.parse(it)
                } ?: ContentType.Text.Plain

                proceedWith(TextContent(body, contentType))
            }
            is ByteArray -> proceedWith(object : OutgoingContent.ByteArrayContent() {
                override val contentLength: Long = body.size.toLong()
                override fun bytes(): ByteArray = body
            })
            is ByteReadChannel -> proceedWith(object : OutgoingContent.ReadChannelContent() {
                override fun readFrom(): ByteReadChannel = body
            })
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
                proceedWith(HttpResponseContainer(info, readRemaining.readBytes()))
            }
            ByteReadChannel::class -> {
                val channel: ByteReadChannel = writer {
                    try {
                        body.copyTo(channel, limit = Long.MAX_VALUE)
                    } catch (cause: CancellationException) {
                        response.cancel(cause)
                    } catch (cause: Throwable) {
                        response.cancel("Receive failed", cause)
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
