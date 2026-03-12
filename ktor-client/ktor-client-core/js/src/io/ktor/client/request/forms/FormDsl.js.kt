/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: JS-specific implementation of Blob-to-ChannelProvider conversion
// ABOUTME: using dynamic JS interop for arrayBuffer() access.

package io.ktor.client.request.forms

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.files.Blob
import kotlin.js.Promise

@OptIn(DelicateCoroutinesApi::class)
internal actual fun blobChannelProvider(blob: Blob): ChannelProvider {
    val size = blob.size.toLong()
    return ChannelProvider(size) {
        GlobalScope.writer {
            val arrayBuffer = blob.asDynamic().arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
            val bytes = Int8Array(arrayBuffer).unsafeCast<ByteArray>()
            channel.writeFully(bytes)
        }.channel
    }
}
