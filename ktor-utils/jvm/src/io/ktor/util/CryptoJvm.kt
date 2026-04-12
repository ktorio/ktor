/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("CryptoKt")
@file:Suppress("FunctionName")

package io.ktor.util

import kotlinx.coroutines.*
import java.security.*

/**
 * Create a digest function with the specified [algorithm] and [salt] provider.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.getDigestFunction)
 *
 * @param algorithm digest algorithm name
 * @param salt a function computing a salt for a particular hash input value
 */
public fun getDigestFunction(algorithm: String, salt: (value: String) -> String): (String) -> ByteArray = { e ->
    getDigest(e, algorithm, salt)
}

private fun getDigest(text: String, algorithm: String, salt: (String) -> String): ByteArray =
    with(MessageDigest.getInstance(algorithm)) {
        update(salt(text).toByteArray())
        digest(text.toByteArray())
    }

/**
 * Compute SHA-1 hash for the specified [bytes]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.sha1)
 */
public actual fun sha1(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA1").digest(bytes)

/**
 * Create [Digest] from specified hash [name].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.Digest)
 */
public actual fun Digest(name: String): Digest = DigestImpl(MessageDigest.getInstance(name))

@JvmInline
private value class DigestImpl(val delegate: MessageDigest) : Digest {
    override fun plusAssign(bytes: ByteArray) {
        delegate.update(bytes)
    }

    override fun reset() {
        delegate.reset()
    }

    override suspend fun build(): ByteArray = delegate.digest()
}

/**
 * Generates a nonce string [length] characters long. Could block if the system's entropy source is empty.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.generateNonceSuspend)
 */
public actual suspend fun generateNonceSuspend(length: Int): String {
    ensureNonceGeneratorRunning()

    val nonce = seedChannel.receive()

    if (nonce.length >= length) {
        return nonce.substring(0, length)
    }

    return generateNonceLoop(nonce, length)
}

/**
 * Generates a nonce string [length] characters long. Could block if the system's entropy source is empty.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.generateNonceBlocking)
 */
public actual fun generateNonceBlocking(length: Int): String {
    val nonce = seedChannel.tryReceive().getOrNull()

    if (nonce != null && nonce.length >= length) {
        return nonce.substring(0, length)
    }

    ensureNonceGeneratorRunning()

    return runBlocking { generateNonceLoop(nonce, length) }
}

private suspend fun generateNonceLoop(initial: String?, length: Int) = buildString(length) {
    if (initial != null) {
        append(initial)
    }

    while (length > this.length) {
        val toAppend = seedChannel.receive()
        append(toAppend, 0, (length - this.length).coerceAtMost(toAppend.length))
    }
}.substring(0, length)
