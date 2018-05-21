package io.ktor.client.features

import io.ktor.cio.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import java.io.*

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

    responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, response) ->
        if (response !is HttpResponse) return@intercept
        when (info.type) {
            ByteArray::class -> proceedWith(HttpResponseContainer(info, response.content.toByteArray()))
            ByteReadChannel::class -> proceedWith(HttpResponseContainer(info, response.content))
            InputStream::class -> proceedWith(
                HttpResponseContainer(info, response.content.toInputStream(response.executionContext))
            )
        }
    }
}
