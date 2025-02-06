/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.sse.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

internal val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.sse.SSE")

/**
 * Indicates if a client engine supports Server-Sent Events (SSE).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSECapability)
 */
public data object SSECapability : HttpClientEngineCapability<Unit>

/**
 * Client Server-Sent Events (SSE) plugin that allows you to establish an SSE connection to a server
 * and receive Server-Sent Events from it.
 * For a simple session, use [ClientSSESession].
 * For a session with deserialization, use [ClientSSESessionWithDeserialization].
 *
 * ```kotlin
 * val client = HttpClient {
 *     install(SSE)
 * }
 *
 * // SSE request
 * client.serverSentEvents("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 *
 * // SSE request with deserialization
 * client.sse({
 *     url("http://localhost:8080/serverSentEvents")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) { // `this` is `ClientSSESessionWithDeserialization`
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * To learn more, see [the SSE](https://en.wikipedia.org/wiki/Server-sent_events)
 * and [the SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSE)
 */
@OptIn(InternalAPI::class)
public val SSE: ClientPlugin<SSEConfig> = createClientPlugin(
    name = "SSE",
    createConfiguration = ::SSEConfig
) {
    val reconnectionTime = pluginConfig.reconnectionTime
    val showCommentEvents = pluginConfig.showCommentEvents
    val showRetryEvents = pluginConfig.showRetryEvents
    val maxReconnectionAttempts = pluginConfig.maxReconnectionAttempts

    on(AfterRender) { request, content ->
        if (getAttributeValue(request, sseRequestAttr) != true) {
            return@on content
        }
        LOGGER.trace { "Sending SSE request to ${request.url}" }
        request.setCapability(SSECapability, Unit)

        val localReconnectionTime = getAttributeValue(request, reconnectionTimeAttr)
        val localShowCommentEvents = getAttributeValue(request, showCommentEventsAttr)
        val localShowRetryEvents = getAttributeValue(request, showRetryEventsAttr)

        request.attributes.put(ResponseAdapterAttributeKey, SSEClientResponseAdapter())
        request.attributes.put(SSEClientForReconnectionAttr, client)
        content.contentType?.let { request.contentType(it) }
        SSEClientContent(
            localReconnectionTime ?: reconnectionTime,
            localShowCommentEvents ?: showCommentEvents,
            localShowRetryEvents ?: showRetryEvents,
            maxReconnectionAttempts,
            currentCoroutineContext(),
            request,
            content
        )
    }

    client.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, session) ->
        val response = context.response
        val request = response.request

        if (request.attributes.getOrNull(sseRequestAttr) != true) {
            LOGGER.trace { "Skipping non SSE response from ${response.request.url}" }
            return@intercept
        }
        checkResponse(response)
        if (session !is SSESession) {
            throw SSEClientException(
                response,
                message = "Expected ${SSESession::class.simpleName} content but was $session"
            )
        }

        LOGGER.trace { "Receive SSE session from ${response.request.url}: $session" }

        val deserializer = response.request.attributes.getOrNull(deserializerAttr)
        val clientSSESession = deserializer?.let {
            ClientSSESessionWithDeserialization(
                context,
                object : SSESessionWithDeserialization {
                    override val incoming: Flow<TypedServerSentEvent<String>> =
                        session.incoming.map { event: ServerSentEvent ->
                            TypedServerSentEvent(event.data, event.event, event.id, event.retry, event.comments)
                        }

                    override val deserializer: (TypeInfo, String) -> Any? = deserializer

                    override val coroutineContext: CoroutineContext = session.coroutineContext
                }
            )
        } ?: ClientSSESession(context, session)
        proceedWith(HttpResponseContainer(info, clientSSESession))
    }
}

/**
 * Represents an exception which can be thrown during client SSE session.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEClientException)
 */
public class SSEClientException(
    public val response: HttpResponse? = null,
    public override val cause: Throwable? = null,
    public override val message: String? = null
) : IllegalStateException()

private fun <T : Any> getAttributeValue(request: HttpRequestBuilder, attributeKey: AttributeKey<T>): T? {
    return request.attributes.getOrNull(attributeKey)
}

private object AfterRender : ClientHook<suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent> {
    override fun install(
        client: HttpClient,
        handler: suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent
    ) {
        val phase = PipelinePhase("AfterRender")
        client.requestPipeline.insertPhaseAfter(HttpRequestPipeline.Render, phase)
        client.requestPipeline.intercept(phase) { content ->
            if (content !is OutgoingContent) return@intercept
            proceedWith(handler(context, content))
        }
    }
}

internal val SSEClientForReconnectionAttr: AttributeKey<HttpClient> = AttributeKey("SSEClientForReconnection")
internal val SSEReconnectionRequestAttr = AttributeKey<Boolean>("SSEReconnectionRequestAttr")

internal fun checkResponse(response: HttpResponse) {
    val status = response.status
    val contentType = response.contentType()

    if (status == HttpStatusCode.NoContent) {
        LOGGER.trace { "Receive status code NoContent for SSE request to ${response.request.url}" }
        return
    }

    if (status != HttpStatusCode.OK) {
        throw SSEClientException(
            response,
            message = "Expected status code ${HttpStatusCode.OK.value} but was ${status.value}"
        )
    }
    if (contentType?.withoutParameters() != ContentType.Text.EventStream) {
        throw SSEClientException(
            response,
            message = "Expected Content-Type ${ContentType.Text.EventStream} but was $contentType"
        )
    }
}
