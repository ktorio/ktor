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
        val randomBytes = allocArray<ByteVarOf<Byte>>(NONCE_SIZE_IN_BYTES)

        // Generates random Data of given length
        // Crashes if the system random number generator is not available
        val result = SecRandomCopyBytes(
            kSecRandomDefault,
            NONCE_SIZE_IN_BYTES.toULong(),
            randomBytes
        )

        if (result == errSecSuccess) {
            val byteArray = ByteArray(NONCE_SIZE_IN_BYTES)
            for (i in 0 until NONCE_SIZE_IN_BYTES) {
                byteArray[i] = randomBytes[i]
            }
            return byteArray
        }

        error("SECURITY FAILURE: Could not generate secure random numbers for Nonce! Result code: $result")
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
