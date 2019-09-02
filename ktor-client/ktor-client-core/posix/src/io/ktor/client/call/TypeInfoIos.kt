/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import kotlin.reflect.*


actual interface Type

object IosType : Type

@PublishedApi
internal open class TypeBase<T>

@UseExperimental(ExperimentalStdlibApi::class)
actual inline fun <reified T> typeInfo(): TypeInfo {
    val kClass = T::class
    return TypeInfo(kClass, IosType, typeOf<T>())
}

/**
 * Check [this] is instance of [type].
 */
internal actual fun Any.instanceOf(type: KClass<*>): Boolean = type.isInstance(this)
