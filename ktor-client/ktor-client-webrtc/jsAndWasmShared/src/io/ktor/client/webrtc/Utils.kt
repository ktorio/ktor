/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import js.array.JsArray
import js.core.JsAny
import web.errors.DOMException

internal expect fun <T : JsAny?> JsArray<T>.toArray(): Array<T>
internal expect fun <T : JsAny?> List<T>.toJs(): JsArray<T>

internal inline fun <T> withSdpException(message: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        throw WebRtc.SdpException(message, e)
    }
}

internal inline fun <T> withIceException(message: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        throw WebRtc.IceException(message, e)
    }
}

internal expect fun Throwable.asDomException(): DOMException?

internal inline fun <T> withPermissionException(mediaType: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        val errorName = e.asDomException()?.name
        // If the user didn't allow using a media device, it should throw NotAllowedError.
        // Older versions of the specification used SecurityError for this instead.
        if (errorName == DOMException.NotAllowedError || errorName == DOMException.SecurityError) {
            throw WebRtcMedia.PermissionException(mediaType)
        }
        throw e
    }
}
