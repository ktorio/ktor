/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.*

private const val NONCE_SIZE_IN_BYTES = 16

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
@InternalAPI
actual fun generateNonce(): String {
    return hex(generateRandomByteArray())
}

private fun generateRandomByteArray(): ByteArray {
    val result = ByteArray(NONCE_SIZE_IN_BYTES)
    for (i in 0..3) {
        val random = arc4random().toLong()
        result[(i * 4) + 3] = (random and 0xFFFF).toByte()
        result[(i * 4) + 2] = ((random ushr 8) and 0xFFFF).toByte()
        result[(i * 4) + 1] = ((random ushr 16) and 0xFFFF).toByte()
        result[(i * 4)] = ((random ushr 24) and 0xFFFF).toByte()
    }
    return result
}

/**
 * Create [Digest] from specified hash [name].
 */
@InternalAPI
actual fun Digest(name: String): Digest = error("[Digest] is not supported on iOS")

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
@KtorExperimentalAPI
actual fun sha1(bytes: ByteArray): ByteArray = error("sha1 currently is not supported in ktor-native")
