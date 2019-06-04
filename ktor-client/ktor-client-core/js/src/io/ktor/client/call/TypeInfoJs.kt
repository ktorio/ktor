/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call


actual interface Type

object JsType : Type

actual inline fun <reified T> typeInfo(): TypeInfo {
    return TypeInfo(T::class, JsType)
}
