/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.reflect.*

internal actual fun availableProcessors(): Int = 1
internal actual fun exitProcess(status: Int): Nothing {
    exit(status)
    error("EXIT")
}

internal actual val defaultWatchPaths: List<String>
    get() = emptyList()

internal actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.Unconfined

internal actual inline fun ApplicationEnvironment.catchOOM(
    cause: Throwable,
    block: () -> Unit
) {
    block()
}

internal actual suspend fun PipelineContext<*, ApplicationCall>.transformPlatform(
    type: KClass<*>,
    channel: ByteReadChannel
): Any? = when (type) {
    Parameters::class -> {
        val contentType = withContentType(call) { call.request.contentType() }
        when {
            contentType.match(ContentType.Application.FormUrlEncoded) -> {
                val string = channel.readText(charset = call.request.contentCharset() ?: Charsets.ISO_8859_1)
                parseQueryString(string)
            }
            else -> null // Respond UnsupportedMediaType? but what if someone else later would like to do it?
        }
    }
    else -> null
}
