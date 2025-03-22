/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.call.*
import io.ktor.sse.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * A session for handling Server-Sent Events (SSE) from a server.
 *
 * Example of usage:
 * ```kotlin
 * client.sse("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 *
 * To learn more, see [the SSE](https://en.wikipedia.org/wiki/Server-sent_events)
 * and [the SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSESession)
*/
public interface SSESession : CoroutineScope {
    /**
     * An incoming Server-Sent Events (SSE) flow.
     *
     * Each [ServerSentEvent] can contain following fields:
     * - [ServerSentEvent.data] data field of the event.
     * - [ServerSentEvent.event] string identifying the type of event.
     * - [ServerSentEvent.id] event ID.
     * - [ServerSentEvent.retry] reconnection time, in milliseconds to wait before reconnecting.
     * - [ServerSentEvent.comments] comment lines starting with a ':' character.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSESession.incoming)
     */
    public val incoming: Flow<ServerSentEvent>
}

/**
 * A session with deserialization support for handling Server-Sent Events (SSE) from a server.
 *
 * Example of usage:
 * ```kotlin
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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSESessionWithDeserialization)
 */
public interface SSESessionWithDeserialization : CoroutineScope {
    /**
     * An incoming Server-Sent Events (SSE) flow.
     *
     * Each [TypedServerSentEvent] can contain following fields:
     * - [TypedServerSentEvent.data] data field of the event. It can be deserialized into an object
     *   of desired type using the [deserialize] function
     * - [TypedServerSentEvent.event] string identifying the type of event.
     * - [TypedServerSentEvent.id] event ID.
     * - [TypedServerSentEvent.retry] reconnection time, in milliseconds to wait before reconnecting.
     * - [TypedServerSentEvent.comments] comment lines starting with a ':' character.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSESessionWithDeserialization.incoming)
     */
    public val incoming: Flow<TypedServerSentEvent<String>>

    /**
     * Deserializer for transforming the `data` field of a `ServerSentEvent` into a desired data object.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSESessionWithDeserialization.deserializer)
     */
    public val deserializer: (TypeInfo, String) -> Any?
}

/**
 * Deserialize the provided [data] into an object of type [T] using the deserializer function
 * defined in the [SSESessionWithDeserialization] interface.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.deserialize)
 *
 * @param data The string data to deserialize.
 * @return The deserialized object of type [T], or null if deserialization is not successful.
 *
 * Example of usage:
 * ```kotlin
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
 */
public inline fun <reified T> SSESessionWithDeserialization.deserialize(data: String?): T? {
    return data?.let {
        deserializer(typeInfo<T>(), data) as? T
    }
}

/**
 * Deserialize the provided [event] data into an object of type [T] using the deserializer function
 * defined in the [SSESessionWithDeserialization] interface.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.deserialize)
 *
 * @param event The Server-sent event containing data to deserialize.
 * @return The deserialized object of type [T], or null if deserialization is not successful.
 *
 * Example of usage:
 * ```kotlin
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
 */
public inline fun <reified T> SSESessionWithDeserialization.deserialize(
    event: TypedServerSentEvent<String>
): T? = deserialize(event.data)

/**
 * A client session for handling Server-Sent Events (SSE) from a server.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.ClientSSESession)
 *
 * @property call The HTTP call associated with the session.
 *
 * Example of usage:
 * ```kotlin
 * client.sse("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 *
 * To learn more, see [the SSE](https://en.wikipedia.org/wiki/Server-sent_events)
 * and [the SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 */
public class ClientSSESession(public val call: HttpClientCall, delegate: SSESession) : SSESession by delegate

/**
 * A client session with deserialization support for handling Server-Sent Events (SSE) from a server.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.ClientSSESessionWithDeserialization)
 *
 * @property call The HTTP call associated with the session.
 *
 * Example of usage:
 * ```kotlin
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
 */
public class ClientSSESessionWithDeserialization(
    public val call: HttpClientCall,
    delegate: SSESessionWithDeserialization
) : SSESessionWithDeserialization by delegate
