/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import kotlin.reflect.*

/**
 * Information about type.
 */
expect interface Type

/**
 * Ktor type information.
 * @param type: source KClass<*>
 * @param reifiedType: type with substituted generics
 */
data class TypeInfo(val type: KClass<*>, val reifiedType: Type)

/**
 * Returns [TypeInfo] for the specified type [T]
 */
expect inline fun <reified T> typeInfo(): TypeInfo

/**
 * Check [this] is instance of [type].
 */
internal expect fun Any.instanceOf(type: KClass<*>): Boolean
