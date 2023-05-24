/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.logging.*

internal val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.sse.ServerSentEvents")

/**
 * Indicates if a client engine supports Server-sent events.
 */
public object ServerSentEventsCapability : HttpClientEngineCapability<Unit> {
    override fun toString(): String = "ServerSentEventsCapability"
}

/**
 * Client Server-sent events plugin.
 *
 * @property reconnectionTimeMillis - time for client to wait before attempting to reconnect.
 */
public class ServerSentEvents(
    public val reconnectionTimeMillis: Long,
) {
    /**
     * [ServerSentEvents] configuration.
     */
    @KtorDsl
    public class Config {
        /**
         * The reconnection time. If the connection to the server is lost,
         * the client will wait for the specified time before attempting to reconnect.
         * Note that this parameter is not supported for some engines.
         */
        public var reconnectionTimeMillis: Long = 1000
    }

    /**
     * Add Server-sent events support for ktor http client.
     */
    public companion object Plugin : HttpClientPlugin<Config, ServerSentEvents> {
        override val key: AttributeKey<ServerSentEvents> = AttributeKey("ServerSentEvents")

        override fun prepare(block: Config.() -> Unit): ServerSentEvents {
            val config = Config().apply(block)
            return ServerSentEvents(
                config.reconnectionTimeMillis,
            )
        }

        override fun install(plugin: ServerSentEvents, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) {
                LOGGER.trace("Sending Server-sent events request ${context.url}")
                context.setCapability(ServerSentEventsCapability, Unit)

                proceedWith(ServerSentEventsContent(plugin.reconnectionTimeMillis))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, session) ->
                if (session !is ClientServerSentEventsSession) {
                    LOGGER.trace("Skipping non Server-sent events response from ${context.request.url}: $session")
                    return@intercept
                }
                LOGGER.trace("Receive Server-sent events session from ${context.request.url}: $session")

                val response = HttpResponseContainer(info, session)
                proceedWith(response)
            }
        }
    }
}

/**
 *  Server-sent event receiving from server.
 *
 *  @property event - a string identifying the type of event described
 *  @property id - the event ID.
 *  @property data - the data field for the message.
 */
public class ServerSentEvent(public val event: String? = null, public val id: String? = null, public val data: String) {
    override fun toString(): String {
        return "ServerSentEvent(event=$event, id=$id, data='$data')"
    }
}

@Suppress("KDocMissingDocumentation")
public class ServerSentEventsException : IllegalStateException {
    public constructor(cause: Throwable?) : super(cause)
    public constructor(message: String) : super(message)
}
