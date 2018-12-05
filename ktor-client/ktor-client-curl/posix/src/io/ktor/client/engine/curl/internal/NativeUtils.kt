package io.ktor.client.engine.curl.internal

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.native.concurrent.*

internal fun ByteArray.copyToBuffer(buffer: CPointer<ByteVar>, size: ULong, position: Int = 0) {
    usePinned { pinned ->
        memcpy(buffer, pinned.addressOf(position), size)
    }
}

internal inline fun <T : Any> T.asStablePointer() = StableRef.create(this).asCPointer()

internal inline fun <reified T : Any> COpaquePointer.fromCPointer(): T = asStableRef<T>().get()

