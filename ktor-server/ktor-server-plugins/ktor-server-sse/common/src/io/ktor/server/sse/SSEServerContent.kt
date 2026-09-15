/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.logging.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * An [OutgoingContent] response object that could be used to `respond()`.
 * It will start Server-Sent events [SSE] session.
 *
 * Please note that you generally shouldn't use this object directly but use [SSE] plugin with routing builders
 * [sse] instead.
 *
 * [handle] function is applied to a session.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.SSEServerContent)
 *
 * @param call that is starting SSE session.
 * @param handle function that is started once SSE session created.
 */
public class SSEServerContent(
    public val call: ApplicationCall,
    public val handle: suspend ServerSSESession.() -> Unit,
    public val serialize: ((TypeInfo, Any) -> String)? = null
) : OutgoingContent.WriteChannelContent() {
    public constructor(
        call: ApplicationCall,
        handle: suspend ServerSSESession.() -> Unit,
    ) : this(call, handle, null)

    override val contentType: ContentType = ContentType.Text.EventStream

    override suspend fun writeTo(channel: ByteWriteChannel) {
        LOGGER.trace { "Starting sse session for ${call.request.uri}" }

        var session: ServerSSESession? = null
        try {
            coroutineScope {
                val defaultSession = DefaultServerSSESession(channel, call, coroutineContext)
                session = when (serialize) {
                    null -> defaultSession
                    else -> object : ServerSSESessionWithSerialization, ServerSSESession by defaultSession {
                        override val serializer: (TypeInfo, Any) -> String = serialize
                    }
                }
                try {
                    session.handle()
                } finally {
                    val heartbeat = call.attributes.getOrNull(heartbeatStateKey)
                    heartbeat?.job?.cancel()
                }
            }
        } finally {
            withContext(NonCancellable) {
                session?.close()
            }
        }
    }

    override fun toString(): String = "SSEServerContent"
}
