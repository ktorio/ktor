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
 * A Server-sent events session.
 *
 * Example of usage:
 * ```kotlin
 * client.sse("http://localhost:8080/sse") {
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 */
public interface SSESession : CoroutineScope {
    /**
     * An incoming server-sent events flow.
     */
    public val incoming: Flow<ServerSentEvent>
}

/**
 * A Server-sent events session.
 *
 * Example of usage:
 * ```kotlin
 * client.sse({
 *     url("http://localhost:8080/serverSentEvents")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) {
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
public interface SSESessionWithDeserialization : CoroutineScope {
    /**
     * An incoming server-sent events flow.
     */
    public val incoming: Flow<TypedServerSentEvent<String>>

    /**
     * Deserializer for transforming the `data` field of a `ServerSentEvent` into a desired data object.
     */
    public val deserializer: (TypeInfo, String) -> Any?
}

/**
 * Deserialize the provided [data] into an object of type [T] using the deserializer function
 * defined in the [SSESessionWithDeserialization] interface.
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
 * }) {
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
 * }) {
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
 * A client Server-sent events session.
 *
 * @property call associated with session.
 *
 * Example of usage:
 * ```kotlin
 * client.sse("http://localhost:8080/sse") {
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 */
public class ClientSSESession(public val call: HttpClientCall, delegate: SSESession) : SSESession by delegate

/**
 * A client Server-sent events session with deserialization support.
 *
 * @property call associated with session.
 *
 * Example of usage:
 * ```kotlin
 * client.sse({
 *     url("http://localhost:8080/serverSentEvents")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) {
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
public class ClientSSESessionWithDeserialization(
    public val call: HttpClientCall,
    delegate: SSESessionWithDeserialization
) : SSESessionWithDeserialization by delegate
