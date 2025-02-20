/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.bodylimit

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*

/**
 * A configuration for [RequestBodyLimit] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.bodylimit.RequestBodyLimitConfig)
 */
public class RequestBodyLimitConfig {
    internal var bodyLimit: (ApplicationCall) -> Long = { Long.MAX_VALUE }

    /**
     * Sets a limit for the maximum allowed size for incoming request bodies.
     * The block should return [Long.MAX_VALUE] if the body size is unlimited.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.bodylimit.RequestBodyLimitConfig.bodyLimit)
     */
    public fun bodyLimit(block: (ApplicationCall) -> Long) {
        bodyLimit = block
    }
}

/**
 * A plugin that limits the maximum allowed size for incoming request bodies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.bodylimit.RequestBodyLimit)
 */
public val RequestBodyLimit: RouteScopedPlugin<RequestBodyLimitConfig> = createRouteScopedPlugin(
    "RequestBodyLimit",
    ::RequestBodyLimitConfig
) {

    val bodyLimit = pluginConfig.bodyLimit

    onCall { call ->
        val limit = bodyLimit(call)
        val contentLength = call.request.contentLength()
        if (contentLength != null && contentLength > limit) {
            throw PayloadTooLargeException(limit)
        }
    }

    on(BeforeReceive) { call, content ->
        val limit = bodyLimit(call)
        if (limit == Long.MAX_VALUE) return@on null

        content.applyLimit(limit)
    }
}

@OptIn(DelicateCoroutinesApi::class)
internal fun ByteReadChannel.applyLimit(limit: Long): ByteReadChannel =
    GlobalScope.writer {
        var total = 0L
        ByteArrayPool.useInstance { array ->
            while (!isClosedForRead) {
                val read = readAvailable(array, 0, array.size)
                if (read <= 0) {
                    continue
                }
                channel.writeFully(array, 0, read)
                total += read
                if (total > limit) {
                    throw PayloadTooLargeException(limit)
                }
            }
            closedCause?.let { throw it }
        }
    }.channel

private object BeforeReceive : Hook<(PipelineCall, ByteReadChannel) -> ByteReadChannel?> {

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: (PipelineCall, ByteReadChannel) -> ByteReadChannel?
    ) {
        pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Before) {
            if (subject !is ByteReadChannel) return@intercept
            val result = handler(context, subject as ByteReadChannel)
            if (result != null) proceedWith(result)
        }
    }
}
