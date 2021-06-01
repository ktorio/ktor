/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.routing

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import java.io.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Receives content for this request.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type.
 */
@OptIn(ExperimentalStdlibApi::class)
public suspend inline fun <reified T : Any> RoutingCall.receiveOrNull(): T? = call.receiveOrNull()

/**
 * Receives content for this request.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
@OptIn(ExperimentalStdlibApi::class)
public suspend inline fun <reified T : Any> RoutingCall.receive(): T = call.receive()

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
public suspend fun <T : Any> RoutingCall.receive(type: KClass<T>): T = call.receive(type)

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
public suspend fun <T : Any> RoutingCall.receive(typeInfo: TypeInfo): T = call.receive(typeInfo)

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type..
 */
public suspend fun <T : Any> RoutingCall.receiveOrNull(typeInfo: TypeInfo): T? = call.receiveOrNull(typeInfo)

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type..
 */
public suspend fun <T : Any> RoutingCall.receiveOrNull(type: KClass<T>): T? = call.receiveOrNull(type)

/**
 * Receives incoming content for this call as [String].
 * @return text received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the [String].
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun RoutingCall.receiveText(): String = call.receive()

/**
 * Receives channel content for this call.
 * @return instance of [ByteReadChannel] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [ByteReadChannel].
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun RoutingCall.receiveChannel(): ByteReadChannel = call.receive()

/**
 * Receives stream content for this call.
 * @return instance of [InputStream] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [InputStream].
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun RoutingCall.receiveStream(): InputStream = call.receive()

/**
 * Receives multipart data for this call.
 * @return instance of [MultiPartData].
 * @throws ContentTransformationException when content cannot be transformed to the [MultiPartData].
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun RoutingCall.receiveMultipart(): MultiPartData = call.receive()

/**
 * Receives form parameters for this call.
 * @return instance of [Parameters].
 * @throws ContentTransformationException when content cannot be transformed to the [Parameters].
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun RoutingCall.receiveParameters(): Parameters = call.receive()
