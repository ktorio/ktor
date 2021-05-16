/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.reflect.*

internal actual suspend fun PipelineContext<*, ApplicationCall>.transformPlatform(
    type: KClass<*>,
    channel: ByteReadChannel
): Any? = when (type) {
    InputStream::class -> receiveGuardedInputStream(channel)
    MultiPartData::class -> multiPartData(channel)
    Parameters::class -> {
        val contentType = withContentType(call) { call.request.contentType() }
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

private fun receiveGuardedInputStream(channel: ByteReadChannel): InputStream {
    checkSafeParking()
    return channel.toInputStream()
}

private fun checkSafeParking() {
    check(safeToRunInPlace()) {
        "Acquiring blocking primitives on this dispatcher is not allowed. " +
            "Consider using async channel or " +
            "doing withContext(Dispatchers.IO) { call.receive<InputStream>().use { ... } } instead."
    }
}

private fun PipelineContext<*, ApplicationCall>.multiPartData(rc: ByteReadChannel): MultiPartData {
    val contentType = call.request.header(HttpHeaders.ContentType)
        ?: throw IllegalStateException("Content-Type header is required for multipart processing")

    val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLong()
    return CIOMultipartDataBase(
        coroutineContext + Dispatchers.Unconfined,
        rc,
        contentType,
        contentLength
    )
}
