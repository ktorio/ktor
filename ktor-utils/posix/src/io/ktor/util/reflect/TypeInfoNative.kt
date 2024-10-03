/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

public actual typealias Type = KType

public actual inline fun <reified T> typeInfo(): TypeInfo = typeInfoImpl(typeOf<T>(), T::class, typeOf<T>())

@PublishedApi
internal fun typeInfoImpl(reifiedType: Type, kClass: KClass<*>, kType: KType): TypeInfo =
    TypeInfo(kClass, reifiedType, kType)

/**
 * Check [this] is instance of [type].
 */
public actual fun Any.instanceOf(type: KClass<*>): Boolean = type.isInstance(this)

public actual val KType.platformType: Type
    get() = this
