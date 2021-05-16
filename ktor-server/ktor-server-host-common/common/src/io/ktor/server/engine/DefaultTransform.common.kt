/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.Any
import kotlin.ByteArray
import kotlin.Long
import kotlin.String
import kotlin.arrayOf
import kotlin.native.concurrent.*
import kotlin.reflect.*

@ThreadLocal
private val ReusableTypes = arrayOf(ByteArray::class, String::class, Parameters::class)

/**
 * Default send transformation
 */
@EngineAPI
public fun ApplicationSendPipeline.installDefaultTransformations() {
    intercept(ApplicationSendPipeline.Render) { value ->
        val transformed = transformDefaultContent(value)
        if (transformed != null) proceedWith(transformed)
    }
}

internal expect suspend fun PipelineContext<*, ApplicationCall>.transformPlatform(
    type: KClass<*>,
    channel: ByteReadChannel
): Any?

internal suspend fun PipelineContext<*, ApplicationCall>.transform(
    type: KClass<*>,
    channel: ByteReadChannel
): Any? = when (type) {
    ByteReadChannel::class -> channel
    ByteArray::class -> channel.toByteArray()
    String::class -> channel.readText(withContentType(call) { call.request.contentCharset() } ?: Charsets.ISO_8859_1)
    else -> transformPlatform(type, channel)
}

/**
 * Default receive transformation
 */
@EngineAPI
public fun ApplicationReceivePipeline.installDefaultTransformations() {
    intercept(ApplicationReceivePipeline.Transform) { query ->
        val channel = query.value as? ByteReadChannel ?: return@intercept
        val transformed = transform(query.typeInfo.type, channel) ?: return@intercept
        proceedWith(ApplicationReceiveRequest(query.typeInfo, transformed, query.typeInfo.type in ReusableTypes))
    }
}

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
    val content = readRemaining()
    if (content.isEmpty) {
        return ""
    }

    return try {
        content.readText(charset)
    } finally {
        content.release()
    }
}
