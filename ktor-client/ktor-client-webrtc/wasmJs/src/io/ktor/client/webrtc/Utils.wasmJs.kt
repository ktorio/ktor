/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import web.errors.DOMException

internal actual fun Throwable.asDomException(): DOMException? {
    return (this as? JsException)?.thrownValue as? DOMException
}
