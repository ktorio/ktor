/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import kotlin.reflect.*


public actual typealias Type = KType

@PublishedApi
internal open class TypeBase<T>

@OptIn(ExperimentalStdlibApi::class)
public actual inline fun <reified T> typeInfo(): TypeInfo {
    val kClass = T::class
    val kotlinType = typeOf<T>()
    return TypeInfo(kClass, kotlinType, kotlinType)
}

/**
 * Check [this] is instance of [type].
 */
internal actual fun Any.instanceOf(type: KClass<*>): Boolean = type.isInstance(this)
