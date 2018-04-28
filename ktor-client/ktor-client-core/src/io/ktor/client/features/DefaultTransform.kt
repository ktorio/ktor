package io.ktor.client.features

import io.ktor.cio.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*

fun HttpClient.defaultTransformers() {
    requestPipeline.intercept(HttpRequestPipeline.Render) { body ->
        when (body) {
            is ByteArray -> proceedWith(object : OutgoingContent.ByteArrayContent() {
                override val contentLength: Long = body.size.toLong()
                override fun bytes(): ByteArray = body
            })
        }
    }

    responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, content) ->
        if (content !is HttpResponse) return@intercept
        when (info.type) {
            ByteArray::class -> proceedWith(HttpResponseContainer(info, content.content.toByteArray()))
        }
    }
}
