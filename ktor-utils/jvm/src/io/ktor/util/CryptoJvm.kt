/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:JvmMultifileClass
@file:JvmName("CryptoKt")
@file:Suppress("FunctionName")

package io.ktor.util

import java.security.MessageDigest

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
    val nonce = tryGenerateNonceFromChannel(length)
    if (nonce is String) {
        return nonce
    }
    ensureNonceGeneratorRunning()
    return if (nonce == null) {
        nonceChannel.receive().substring(0, length)
    } else {
        generateNonceLong(nonce as StringBuilder, length)
    }
}

/**
 * Generates a nonce string [length] characters long. Could block if the system's entropy source is empty.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.generateNonceBlocking)
 */
public actual fun generateNonceBlocking(length: Int): String {
    val nonce = tryGenerateNonceFromChannel(length)
    if (nonce is String) {
        return nonce
    }
    ensureNonceGeneratorRunning()
    val acc = if (nonce == null) StringBuilder(length) else nonce as StringBuilder
    return generateNonceSynchronously(acc, length)
}

/**
 * Attempts to read a nonce from [nonceChannel] without blocking or suspending.
 *
 * The return type is [Any] to avoid wrapper allocations on the hot path when [length]
 * equals [NONCE_SIZE_IN_CHARS] and the channel contains a prefetched nonce.
 *
 * Return value semantics:
 * - [String] — a complete nonce of the requested [length]; callers may return it directly.
 * - `null` — only when [length] equals [NONCE_SIZE_IN_CHARS] and the channel is empty.
 * - [StringBuilder] — for non-default lengths, a partial accumulation when the channel was drained
 *   before [length] characters were collected; may be empty if nothing was received.
 */
private fun tryGenerateNonceFromChannel(length: Int): Any? {
    if (length == NONCE_SIZE_IN_CHARS) {
        return nonceChannel.tryReceive().getOrNull()
    }
    val builder = StringBuilder(length)
    while (true) {
        val nonce = nonceChannel.tryReceive().getOrNull()
            ?: return builder
        builder.append(nonce, 0, (length - builder.length).coerceAtMost(nonce.length))
        if (builder.length == length) {
            return builder.toString()
        }
    }
}

private suspend fun generateNonceLong(acc: StringBuilder, length: Int): String {
    while (length > acc.length) {
        val toAppend = nonceChannel.receive()
        acc.append(toAppend, 0, (length - acc.length).coerceAtMost(toAppend.length))
    }
    return acc.toString()
}
