/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.cinterop.*
import platform.posix.*

private const val NONCE_SIZE_IN_BYTES = 16

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
@InternalAPI
actual fun generateNonce(): String {
    return hex(generateRandomByteArray())
}

private fun generateRandomByteArray(size: Int = NONCE_SIZE_IN_BYTES): ByteArray {
    memScoped {
        val result = ByteArray(size)
        val tmp = allocArray<ByteVar>(size)
        val ptr = tmp.getPointer(this)
        val file = fopen("/dev/urandom", "rb")
        if (file != null) {
            fread(ptr, 1.convert(), result.size.convert(), file)
            for (n in result.indices) result[n] = ptr[n]
            fclose(file)
            return result
        }
        error("SECURITY FAILURE: Could not generate random byte array! Reason: '/dev/urandom' could not be found")
    }
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
