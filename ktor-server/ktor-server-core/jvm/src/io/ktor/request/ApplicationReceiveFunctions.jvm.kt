/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.request

import io.ktor.application.*
import io.ktor.util.reflect.*
import java.io.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Receives stream content for this call.
 * @return instance of [InputStream] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [InputStream].
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun ApplicationCall.receiveStream(): InputStream = receive()

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
public suspend fun <T : Any> ApplicationCall.receive(type: KClass<T>): T {
    val kotlinType = type.starProjectedType
    return receive(TypeInfo(type, kotlinType.javaType, kotlinType))
}

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type..
 */
@Deprecated("Use KType instead", level = DeprecationLevel.WARNING)
public suspend fun <T : Any> ApplicationCall.receiveOrNull(type: KClass<T>): T? {
    return try {
        receive(type)
    } catch (cause: ContentTransformationException) {
        application.log.debug("Conversion failed, null returned", cause)
        null
    }
}
