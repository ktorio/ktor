/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import js.array.JsArray
import js.core.JsAny
import web.errors.DOMException

internal actual fun <T : JsAny?> JsArray<T>.toArray(): Array<T> = this

internal actual fun <T : JsAny?> List<T>.toJs(): JsArray<T> = toTypedArray()

internal actual fun Throwable.asDomException(): DOMException? = this as? DOMException
