/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: WasmJS-specific implementation of Blob-to-ChannelProvider conversion
// ABOUTME: using js() interop functions for arrayBuffer() access.

package io.ktor.client.request.forms

import io.ktor.client.fetch.ArrayBuffer
import io.ktor.client.utils.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import kotlin.js.Promise

@Suppress("UNUSED_PARAMETER")
private fun blobArrayBuffer(blob: Blob): Promise<ArrayBuffer> = js("blob.arrayBuffer()")

@Suppress("UNUSED_PARAMETER")
private fun createUint8Array(buffer: ArrayBuffer): Uint8Array = js("new Uint8Array(buffer)")

@OptIn(DelicateCoroutinesApi::class)
internal actual fun blobChannelProvider(blob: Blob): ChannelProvider {
    val size = blob.size.toInt().toLong()
    return ChannelProvider(size) {
        GlobalScope.writer {
            val arrayBuffer = blobArrayBuffer(blob).await<ArrayBuffer>()
            val bytes = createUint8Array(arrayBuffer).asByteArray()
            channel.writeFully(bytes)
        }.channel
    }
}
