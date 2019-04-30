/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call


actual interface Type {}

object IosType : Type {}

@PublishedApi()
internal open class TypeBase<T>

actual inline fun <reified T> typeInfo(): TypeInfo {
    val kClass = T::class
    return TypeInfo(kClass, IosType)
}
