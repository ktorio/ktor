package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*

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
            is ByteArray -> proceedWith(object : OutgoingContent.ByteArrayContent() {
                override val contentLength: Long = body.size.toLong()
                override fun bytes(): ByteArray = body
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
                response.cancel()
                proceedWith(HttpResponseContainer(info, Unit))
            }
            ByteReadPacket::class,
            Input::class -> proceedWith(HttpResponseContainer(info, body.readRemaining()))
            ByteArray::class -> {
                val readRemaining = body.readRemaining(contentLength)
                proceedWith(HttpResponseContainer(info, readRemaining.readBytes()))
            }
        }
    }

    platformDefaultTransformers()
}

internal expect fun HttpClient.platformDefaultTransformers()
