/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import kotlin.reflect.*


public actual interface Type

public object JsType : Type

@OptIn(ExperimentalStdlibApi::class)
public actual inline fun <reified T> typeInfo(): TypeInfo = try {
    TypeInfo(T::class, JsType, typeOf<T>())
} catch (_: dynamic) {
    TypeInfo(T::class, JsType)
}

/**
 * Check [this] is instance of [type].
 */
internal actual fun Any.instanceOf(type: KClass<*>): Boolean = type.isInstance(this)
