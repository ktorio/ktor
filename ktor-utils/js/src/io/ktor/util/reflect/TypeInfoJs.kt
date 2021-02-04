/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

public actual interface Type

public object JsType : Type

@OptIn(ExperimentalStdlibApi::class)
public actual inline fun <reified T> typeInfo(): TypeInfo = try {
    typeInfoImpl(JsType, T::class, typeOf<T>())
} catch (_: dynamic) {
    typeInfoImpl(JsType, T::class, null)
}

public fun typeInfoImpl(reifiedType: Type, kClass: KClass<*>, kType: KType?): TypeInfo =
    TypeInfoImpl(kClass, reifiedType, kType)

/**
 * Check [this] is instance of [type].
 */
public actual fun Any.instanceOf(type: KClass<*>): Boolean = type.isInstance(this)

internal actual val KType.platformType: Type
    get() = JsType
