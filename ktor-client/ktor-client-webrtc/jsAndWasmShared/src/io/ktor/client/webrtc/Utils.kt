/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import js.array.JsArray
import js.buffer.ArrayBuffer
import js.core.JsAny
import js.core.JsPrimitives.toByte
import js.core.JsPrimitives.toJsByte
import js.typedarrays.Int8Array
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import web.errors.DOMException
import web.errors.NotAllowedError
import web.errors.SecurityError
import web.events.Event
import web.events.EventHandler
import web.events.EventTarget
import web.events.HasTargets

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

internal fun ByteArray.toArrayBuffer(): ArrayBuffer {
    val array = Int8Array<ArrayBuffer>(size)
    repeat(size) { i ->
        array[i] = this[i].toJsByte()
    }
    return array.buffer
}

internal fun ArrayBuffer.toByteArray(): ByteArray {
    val arr = Int8Array(this)
    return ByteArray(byteLength) { arr[it].toByte() }
}

// A helper to run the event handler in the coroutine scope
@Suppress("BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER")
internal fun <E : Event, C : EventTarget, T : EventTarget, D> eventHandler(
    coroutineScope: CoroutineScope,
    handler: suspend (D) -> Unit
) where D : E, D : HasTargets<C, T> =
    EventHandler<E, C, T, D> { event ->
        // There is no need for extra dispatching, keep the concurrency structured.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { handler(event) }
    }
