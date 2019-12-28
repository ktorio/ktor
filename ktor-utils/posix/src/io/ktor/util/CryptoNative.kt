/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.cinterop.*
import platform.Security.*

private const val NONCE_SIZE_IN_BYTES = 16

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
@InternalAPI
actual fun generateNonce(): String {
    return hex(generateNonceByteArray())
}

private fun generateNonceByteArray(): ByteArray {
    memScoped {
        val array = allocArray<ByteVarOf<Byte>>(NONCE_SIZE_IN_BYTES)
        val status = SecRandomCopyBytes(
            kSecRandomDefault,
            NONCE_SIZE_IN_BYTES.toULong(),
            array
        )
        if (status == errSecSuccess) {
            val result = ByteArray(NONCE_SIZE_IN_BYTES)
            for (i in 0 until NONCE_SIZE_IN_BYTES) {
                result[i] = array[i]
            }
            return result
        }
        error("Nonce could not be generated")
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
