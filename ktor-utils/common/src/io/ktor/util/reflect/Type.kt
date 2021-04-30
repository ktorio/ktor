/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

/**
 * Information about type.
 */
public expect interface Type

internal expect val KType.platformType: Type

/**
 * Ktor type information.
 * @property type: source KClass<*>
 * @property reifiedType: type with substituted generics
 * @property kotlinType: kotlin reified type with all generic type parameters.
 */
public data class TypeInfo(
    public val type: KClass<*>,
    public val reifiedType: Type,
    public val kotlinType: KType? = null
)

/**
 * Returns [TypeInfo] for the specified type [T]
 */
public expect inline fun <reified T> typeInfo(): TypeInfo

/**
 * Check [this] is instance of [type].
 */
public expect fun Any.instanceOf(type: KClass<*>): Boolean

@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T> tryGetType(): KType? = try {
    // We need to wrap getting type in try catch because of https://youtrack.jetbrains.com/issue/KT-42913
    typeOf<T>()
} catch (cause: Throwable) {
    null
}
