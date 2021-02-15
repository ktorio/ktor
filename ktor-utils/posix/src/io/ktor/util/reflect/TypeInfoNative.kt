/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

public actual typealias Type = KType

@OptIn(ExperimentalStdlibApi::class)
public actual inline fun <reified T> typeInfo(): TypeInfo {
    val kClass = T::class
    val kotlinType = typeOf<T>()
    return typeInfoImpl(kotlinType, kClass, kotlinType)
}

@PublishedApi
internal fun typeInfoImpl(reifiedType: Type, kClass: KClass<*>, kType: KType): TypeInfo =
    TypeInfoImpl(kClass, reifiedType, kType)

/**
 * Check [this] is instance of [type].
 */
public actual fun Any.instanceOf(type: KClass<*>): Boolean = type.isInstance(this)

internal actual val KType.platformType: Type
    get() = this
