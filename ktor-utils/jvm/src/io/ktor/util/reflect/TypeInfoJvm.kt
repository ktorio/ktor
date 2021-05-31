/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

public actual typealias Type = java.lang.reflect.Type

@PublishedApi
@Deprecated("Will be removed")
internal open class TypeBase<T>

@OptIn(ExperimentalStdlibApi::class)
public actual inline fun <reified T> typeInfo(): TypeInfo {
    val kType = typeOf<T>()
    val reifiedType = kType.javaType
    return typeInfoImpl(reifiedType, T::class, kType)
}

public fun typeInfoImpl(reifiedType: Type, kClass: KClass<*>, kType: KType): TypeInfo =
    TypeInfoImpl(kClass, reifiedType, kType)

/**
 * Check [this] is instance of [type].
 */
public actual fun Any.instanceOf(type: KClass<*>): Boolean = type.java.isInstance(this)

@OptIn(ExperimentalStdlibApi::class)
internal actual val KType.platformType: Type
    get() = javaType
