/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import web.errors.DOMException
import kotlin.js.toArray as toKotlinArray

internal actual fun <T : JsAny?> JsArray<T>.toArray(): Array<T> = toKotlinArray()

internal actual fun <T : JsAny?> List<T>.toJs(): JsArray<T> = toJsArray()

internal actual fun Throwable.asDomException(): DOMException? {
    return (this as? JsException)?.thrownValue as? DOMException
}
