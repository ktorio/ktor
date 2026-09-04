/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.coroutines.runBlocking
import java.util.concurrent.*
import javax.crypto.*
import javax.crypto.spec.*

/**
 * Stateless nonce manager implementation with HMAC verification and timeout.
 * Every nonce provided by this manager consists of a random part, timestamp, and HMAC.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.StatelessHmacNonceManager)
 *
 * @property keySpec secret key spec for HMAC
 * @property algorithm HMAC algorithm name, `HmacSHA256` by default
 * @property timeoutMillis specifies the amount of time for a nonce to be considered valid
 * @property nonceGenerator function that produces random values
 */
public class StatelessHmacNonceManager private constructor(
    @Deprecated("This will become private") public val keySpec: SecretKeySpec,
    @Deprecated("This will become private") public val algorithm: String = "HmacSHA256",
    @Deprecated("This will become private") public val timeoutMillis: Long = 6000,
    private val suspendNonceGenerator: suspend () -> String,
    @Suppress("UNUSED_PARAMETER")
    dummy: Unit
) : NonceManager {

    public constructor(
        keySpec: SecretKeySpec,
        algorithm: String = "HmacSHA256",
        timeoutMillis: Long = 6000,
        nonceGenerator: suspend () -> String = { generateNonceSuspend() },
    ) : this (keySpec, algorithm, timeoutMillis, suspendNonceGenerator = nonceGenerator, dummy = Unit)

    /**
     * Helper constructor that makes a secret key from [key] ByteArray
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.StatelessHmacNonceManager.StatelessHmacNonceManager)
     */
    public constructor(
        key: ByteArray,
        algorithm: String = "HmacSHA256",
        timeoutMillis: Long = 60000,
        nonceGenerator: suspend () -> String = { generateNonceSuspend() },
    ) : this(
        SecretKeySpec(key, algorithm),
        algorithm,
        timeoutMillis,
        suspendNonceGenerator = nonceGenerator,
        dummy = Unit
    )

    @Deprecated("Non-suspend nonce generator is deprecated", level = DeprecationLevel.HIDDEN)
    public constructor(
        keySpec: SecretKeySpec,
        algorithm: String = "HmacSHA256",
        timeoutMillis: Long = 60000,
        nonceGenerator: () -> String = DEFAULT_LEGACY_NONCE_GENERATOR,
    ) : this(
        keySpec,
        algorithm,
        timeoutMillis,
        suspendNonceGenerator = if (nonceGenerator === DEFAULT_LEGACY_NONCE_GENERATOR) {
            { generateNonceSuspend() }
        } else {
            { nonceGenerator() }
        },
        dummy = Unit
    )

    /**
     * Helper constructor that makes a secret key from [key] ByteArray
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.StatelessHmacNonceManager.StatelessHmacNonceManager)
     */
    @Deprecated("Non-suspend nonce generator is deprecated", level = DeprecationLevel.HIDDEN)
    public constructor(
        key: ByteArray,
        algorithm: String = "HmacSHA256",
        timeoutMillis: Long = 60000,
        nonceGenerator: () -> String = DEFAULT_LEGACY_NONCE_GENERATOR,
    ) : this(
        SecretKeySpec(key, algorithm),
        algorithm,
        timeoutMillis,
        suspendNonceGenerator = if (nonceGenerator === DEFAULT_LEGACY_NONCE_GENERATOR) {
            { generateNonceSuspend() }
        } else {
            { nonceGenerator() }
        },
        dummy = Unit
    )

    @Deprecated("This will become private")
    public val nonceGenerator: () -> String = {
        // blocking, but legacy nonceGenerator shouldn't be used much
        runBlocking { suspendNonceGenerator() }
    }

    /**
     * MAC length in bytes
     */
    @Suppress("DEPRECATION")
    private val macLength = Mac.getInstance(algorithm).let { mac ->
        mac.init(keySpec)
        mac.macLength
    }

    @Suppress("DEPRECATION")
    override suspend fun newNonce(): String {
        val random = suspendNonceGenerator()
        val time = System.nanoTime().toString(16).padStart(16, '0')

        val mac = Mac.getInstance(algorithm).apply {
            init(keySpec)
            update("$random:$time".toByteArray(Charsets.ISO_8859_1))
        }.doFinal().toHexString()

        return "$random+$time+$mac"
    }

    @Suppress("DEPRECATION")
    override suspend fun verifyNonce(nonce: String): Boolean {
        val parts = nonce.split('+')
        if (parts.size != 3) return false
        val (random, time, mac) = parts

        if (random.length < 8) return false
        if (mac.length != macLength * 2) return false
        if (time.length != 16) return false

        val nanoTime = time.toLong(16)
        if (nanoTime + TimeUnit.MILLISECONDS.toNanos(timeoutMillis) < System.nanoTime()) return false

        val computedMac = Mac.getInstance(algorithm).apply {
            init(keySpec)
            update("$random:$time".toByteArray(Charsets.ISO_8859_1))
        }.doFinal().toHexString()

        var validCount = 0
        for (i in 0 until minOf(computedMac.length, mac.length)) {
            if (computedMac[i] == mac[i]) {
                validCount++
            }
        }

        return validCount == macLength * 2
    }

    private companion object {
        private val DEFAULT_LEGACY_NONCE_GENERATOR: () -> String = { generateNonceBlocking() }
    }
}
