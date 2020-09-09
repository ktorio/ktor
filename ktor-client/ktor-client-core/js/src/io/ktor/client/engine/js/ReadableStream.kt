/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import kotlinx.coroutines.*
import org.khronos.webgl.*
import kotlin.coroutines.*
import kotlin.js.*

internal external interface ReadableStream {
    public fun getReader(): ReadableStreamReader
}

internal external interface ReadResult {
    val done: Boolean
    val value: Uint8Array?
}

internal external interface ReadableStreamReader {
    public fun cancel(reason: dynamic): Promise<dynamic>
    public fun read(): Promise<ReadResult>
}

internal suspend fun ReadableStreamReader.readChunk(): Uint8Array? = suspendCancellableCoroutine { continuation ->
    read().then {
        val chunk = it.value
        val result = if (it.done || chunk == null) null else chunk
        continuation.resumeWith(Result.success(result))
    }.catch { cause ->
        continuation.resumeWithException(cause)
    }
}

@Suppress("UnsafeCastFromDynamic")
internal fun Uint8Array.asByteArray(): ByteArray {
    return Int8Array(buffer, byteOffset, length).asDynamic()
}
