/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import platform.posix.*

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
@InternalAPI
public actual fun generateNonce(): String {
    val builder = StringBuilder()
    repeat(16) {
        builder.append(rand().toChar())
    }

    return builder.toString()
}

/**
 * Create [Digest] from specified hash [name].
 */
@InternalAPI
public actual fun Digest(name: String): Digest = error("[Digest] is not supported on iOS")

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
public actual fun sha1(bytes: ByteArray): ByteArray = error("sha1 currently is not supported in ktor-native")
