package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import kotlinx.io.streams.*
import java.io.*
import java.nio.charset.*

fun ApplicationSendPipeline.installDefaultTransformations() {
    intercept(ApplicationSendPipeline.Render) { value ->
        val transformed = transformDefaultContent(value)
        if (transformed != null)
            proceedWith(transformed)
    }
}

fun ApplicationReceivePipeline.installDefaultTransformations() {
    intercept(ApplicationReceivePipeline.Transform) { query ->
        val channel = query.value as? ByteReadChannel ?: return@intercept
        val transformed: Any? = when (query.type) {
            ByteReadChannel::class -> channel
            ByteArray::class -> channel.toByteArray()
            InputStream::class -> channel.toInputStream()
            MultiPartData::class -> multiPartData(channel)
            String::class -> channel.readText(charset = call.request.contentCharset() ?: Charsets.ISO_8859_1)
            Parameters::class -> {
                val contentType = call.request.contentType()
                when {
                    contentType.match(ContentType.Application.FormUrlEncoded) -> {
                        val string = channel.readText(charset = call.request.contentCharset() ?: Charsets.ISO_8859_1)
                        parseQueryString(string)
                    }
                    contentType.match(ContentType.MultiPart.FormData) -> {
                        Parameters.build {
                            multiPartData(channel).forEachPart { part ->
                                if (part is PartData.FormItem) {
                                    part.name?.let { partName ->
                                        append(partName, part.value)
                                    }
                                }

                                part.dispose()
                            }
                        }
                    }
                    else -> null // Respond UnsupportedMediaType? but what if someone else later would like to do it?
                }
            }
            else -> null
        }
        if (transformed != null)
            proceedWith(ApplicationReceiveRequest(query.type, transformed))
    }
}

private fun PipelineContext<*, ApplicationCall>.multiPartData(rc: ByteReadChannel): MultiPartData {
    val contentType = call.request.header(HttpHeaders.ContentType)
            ?: throw IllegalStateException("Content-Type header is required for multipart processing")

    val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLong()
    return CIOMultipartDataBase(Unconfined, rc, contentType, contentLength)
}

private suspend fun ByteReadChannel.readText(
        charset: Charset
): String {
    if (isClosedForRead) return ""

    val content = readRemaining(Long.MAX_VALUE)

    return try {
        if (charset == Charsets.UTF_8) content.readText()
        else content.inputStream().reader(charset).readText()
    } finally {
        content.release()
    }
}

