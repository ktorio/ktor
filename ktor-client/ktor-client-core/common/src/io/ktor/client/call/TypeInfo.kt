/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.call

import kotlin.reflect.*
import io.ktor.util.reflect.Type as NewType
import io.ktor.util.reflect.TypeInfo as NewTypeInfo
import io.ktor.util.reflect.instanceOf as newInstanceOf
import io.ktor.util.reflect.typeInfo as newTypeInfo

/**
 * Information about type.
 */
@Deprecated(
    "This was moved to another package.",
    replaceWith = ReplaceWith("Type", "io.ktor.util.reflect.Type")
)
public typealias Type = NewType

@Deprecated(
    "This was moved to another package.",
    replaceWith = ReplaceWith("TypeBase", "io.ktor.util.reflect.TypeBase")
)
@PublishedApi
internal open class TypeBase<T>

/**
 * Ktor type information.
 * @param type: source KClass<*>
 * @param reifiedType: type with substituted generics
 * @param kotlinType: kotlin reified type with all generic type parameters.
 */
@Deprecated(
    "This was moved to another package.",
    replaceWith = ReplaceWith("TypeInfo", "io.ktor.util.reflect.TypeInfo")
)
public data class TypeInfo(
    override val type: KClass<*>,
    override val reifiedType: Type,
    override val kotlinType: KType? = null
) : NewTypeInfo

/**
 * Returns [TypeInfo] for the specified type [T]
 */
@Deprecated(
    "This was moved to another package.",
    replaceWith = ReplaceWith("typeInfo<T>()", "io.ktor.util.reflect.typeInfo")
)
public inline fun <reified T> typeInfo(): TypeInfo {
    val info = newTypeInfo<T>()
    return TypeInfo(info.type, info.reifiedType, info.kotlinType)
}

/**
 * Check [this] is instance of [type].
 */
@Deprecated(
    "This was moved to another package.",
    replaceWith = ReplaceWith("this.instanceOf(type)", "io.ktor.util.reflect.instanceOf")
)
internal fun Any.instanceOf(type: KClass<*>): Boolean = newInstanceOf(type)
