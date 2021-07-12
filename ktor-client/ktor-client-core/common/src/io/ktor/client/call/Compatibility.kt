/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.statement.*
import io.ktor.util.reflect.*

/**
 * Tries to receive the payload of the [response] as an specific type [T].
 *
 * @throws NoTransformationFoundException If no transformation is found for the type [T].
 * @throws DoubleReceiveException If already called [receive].
 */
@Deprecated("Use `body` method instead", replaceWith = ReplaceWith("this.body<T>()"), level = DeprecationLevel.ERROR)
public suspend inline fun <reified T> HttpClientCall.receive(): T = error("Use `body` method instead")

/**
 * Tries to receive the payload of the [response] as an specific type [T].
 *
 * @throws NoTransformationFoundException If no transformation is found for the type [T].
 * @throws DoubleReceiveException If already called [receive].
 */
@Deprecated("Use `body` method instead", replaceWith = ReplaceWith("this.body<T>()"), level = DeprecationLevel.ERROR)
public suspend inline fun <reified T> HttpResponse.receive(): T = error("Use `body` method instead")

/**
 * Tries to receive the payload of the [response] as a specific expected type provided in [info].
 * Returns [response] if [info] corresponds to [HttpResponse].
 *
 * @throws NoTransformationFoundException If no transformation is found for the type [info].
 * @throws DoubleReceiveException If already called [receive].
 */
@Suppress("UNUSED_PARAMETER")
@Deprecated("Use `body` method instead", replaceWith = ReplaceWith("this.body(info)"), level = DeprecationLevel.ERROR)
public suspend fun receive(info: TypeInfo): Any = error("Use `body` method instead")
