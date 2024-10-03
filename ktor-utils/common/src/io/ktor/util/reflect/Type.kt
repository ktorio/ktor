/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

/**
 * Information about type.
 */
public expect interface Type

public expect val KType.platformType: Type

/**
 * Ktor type information.
 * @property type Source KClass<*>
 * @property reifiedType Type with substituted generics
 * @property kotlinType Kotlin reified type with all generic type parameters.
 */
public data class TypeInfo(
    public val type: KClass<*>,
    public val reifiedType: Type,
    public val kotlinType: KType? = null
) {

    override fun hashCode(): Int {
        return kotlinType?.hashCode() ?: type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeInfo) return false

        return if (kotlinType != null || other.kotlinType != null) {
            kotlinType == other.kotlinType
        } else {
            type == other.type
        }
    }
}

/**
 * Returns [TypeInfo] for the specified type [T]
 */
public expect inline fun <reified T> typeInfo(): TypeInfo

/**
 * Check [this] is instance of [type].
 */
public expect fun Any.instanceOf(type: KClass<*>): Boolean

@PublishedApi
internal inline fun <reified T> typeOfOrNull(): KType? = try {
    // We need to wrap getting a type in try catch because of:
    // - KT-42913
    // - KTOR-7479 (R8 in full mode strips class signatures)
    typeOf<T>()
} catch (_: Throwable) {
    null
}
