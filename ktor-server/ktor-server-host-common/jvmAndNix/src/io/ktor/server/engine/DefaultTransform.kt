/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

private val ReusableTypes = arrayOf(ByteArray::class, String::class, Parameters::class)

/**
 * Default send transformation
 */
public fun ApplicationSendPipeline.installDefaultTransformations() {
    intercept(ApplicationSendPipeline.Render) { value ->
        val transformed = transformDefaultContent(call, value)
        if (transformed != null) proceedWith(transformed)
    }
}

/**
 * Default receive transformation
 */
public fun ApplicationReceivePipeline.installDefaultTransformations() {
    intercept(ApplicationReceivePipeline.Transform) { body ->
        val channel = body as? ByteReadChannel ?: return@intercept

        val transformed: Any? = when (call.receiveType.type) {
            ByteReadChannel::class -> channel
            ByteArray::class -> channel.toByteArray()
            String::class -> channel.readText(
                charset = withContentType(call) { call.request.contentCharset() } ?: Charsets.UTF_8
            )
            Parameters::class -> {
                val contentType = withContentType(call) { call.request.contentType() }
                when {
                    contentType.match(ContentType.Application.FormUrlEncoded) -> {
                        val string = channel.readText(charset = call.request.contentCharset() ?: Charsets.UTF_8)
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
            else -> defaultPlatformTransformations(body)
        }
        if (transformed != null) {
            proceedWith(transformed)
        }
    }
}

internal expect suspend fun PipelineContext<Any, ApplicationCall>.defaultPlatformTransformations(
    query: Any
): Any?

internal expect fun PipelineContext<*, ApplicationCall>.multiPartData(rc: ByteReadChannel): MultiPartData

internal inline fun <R> withContentType(call: ApplicationCall, block: () -> R): R = try {
    block()
} catch (parseFailure: BadContentTypeFormatException) {
    throw BadRequestException(
        "Illegal Content-Type header format: ${call.request.headers[HttpHeaders.ContentType]}",
        parseFailure
    )
}

internal suspend fun ByteReadChannel.readText(
    charset: Charset
): String {
    val content = readRemaining(Long.MAX_VALUE)
    if (content.isEmpty) {
        return ""
    }

    return try {
        if (charset == Charsets.UTF_8 || charset == Charsets.ISO_8859_1) {
            content.readText()
        } else {
            content.readTextWithCustomCharset(charset)
        }
    } finally {
        content.release()
    }
}

internal expect fun ByteReadPacket.readTextWithCustomCharset(charset: Charset): String
