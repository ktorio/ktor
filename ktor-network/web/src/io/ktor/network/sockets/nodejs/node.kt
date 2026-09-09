/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

// js.Error
internal external interface JsError : JsAny {
    val message: String?
}

internal expect fun JsError.toThrowable(): Throwable
internal expect fun Throwable.toJsError(): JsError?

internal expect fun jsTypeOf(a: JsAny): String

internal expect suspend fun loadNodeNet(): NodeNet
internal expect suspend fun loadNodeDgram(): NodeDgram

private fun <T : JsAny> createJsObject(): T = js("({})")
internal fun <T : JsAny> createJsObject(block: T.() -> Unit): T = createJsObject<T>().apply(block)
