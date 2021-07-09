/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.coroutines.*
import org.khronos.webgl.*
import kotlin.js.*

private const val NONCE_SIZE_IN_BYTES = 8

/**
 * Generates a nonce string.
 */
@InternalAPI
public actual fun generateNonce(): String {
    val buffer = ByteArray(NONCE_SIZE_IN_BYTES)
    if (PlatformUtils.IS_NODE) {
        _crypto.randomFillSync(buffer)
    } else {
        _crypto.getRandomValues(buffer)
    }
    return hex(buffer)
}

/**
 * Create [Digest] from specified hash [name].
 */
@InternalAPI
public actual fun Digest(name: String): Digest = object : Digest {
    private val state = mutableListOf<ByteArray>()
    override fun plusAssign(bytes: ByteArray) {
        state += bytes
    }

    override fun reset() {
        state.clear()
    }

    override suspend fun build(): ByteArray {
        val snapshot = state.reduce { a, b -> a + b }
        val digestBuffer = _crypto.subtle.digest(name, snapshot).asDeferred().await()
        val digestView = DataView(digestBuffer)
        return ByteArray(digestView.byteLength) { digestView.getUint8(it) }
    }
}

// Variable is renamed to `_crypto` so it wouldn't clash with existing `crypto` variable.
// JS IR backend doesn't reserve names accessed inside js("") calls
private val _crypto: Crypto by lazy { // lazy because otherwise it's untestable due to evaluation order
    if (PlatformUtils.IS_NODE) {
        js("eval('require')('crypto')")
    } else {
        js("(window.crypto ? window.crypto : window.msCrypto)")
    }
}

private external class Crypto {
    val subtle: SubtleCrypto

    public fun getRandomValues(array: ByteArray)

    public fun randomFillSync(array: ByteArray)
}

private external class SubtleCrypto {
    public fun digest(algoName: String, buffer: ByteArray): Promise<ArrayBuffer>
}

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
public actual fun sha1(bytes: ByteArray): ByteArray = error("sha1 currently is not supported in ktor-js")
