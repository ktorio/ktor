/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Generates a nonce string [length] characters long. Could suspend if the system's entropy source is empty.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.generateNonceSuspend)
 */
public actual suspend fun generateNonceSuspend(length: Int): String = generateNonceBlocking(length)

/**
 * Generates a nonce string [length] characters long. Could block if the system's entropy source is empty.
 *
 * [Report a
 * problem](https://ktor.io/feedback/?fqname=io.ktor.util.generateNonceBlocking)
 */
public actual fun generateNonceBlocking(length: Int): String {
    val bytes = ByteArray(length / 2 + 1)
    secureRandom(bytes)
    return bytes.toHexString().substring(0, length)
}

internal expect fun secureRandom(bytes: ByteArray)

/**
 * Create [Digest] from specified hash [name].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.Digest)
 */
public actual fun Digest(name: String): Digest = error("[Digest] is not supported on Darwin")

/**
 * Compute SHA-1 hash for the specified [bytes]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.sha1)
 */
public actual fun sha1(bytes: ByteArray): ByteArray = Sha1().digest(bytes)
