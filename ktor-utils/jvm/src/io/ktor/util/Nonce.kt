/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.slf4j.*
import java.security.*
import kotlin.random.asKotlinRandom

private const val SHA1PRNG = "SHA1PRNG"

private val SECURE_RANDOM_PROVIDERS: List<String> = listOf(
    "NativePRNGNonBlocking",
    "WINDOWS-PRNG",
    "DRBG"
)

private const val SECURE_RESEED_PERIOD = 30_000

private const val SECURE_NONCE_COUNT = 8

private const val INSECURE_NONCE_COUNT_FACTOR = 4

private const val NONCE_SIZE_IN_BYTES = 16

private const val RANDOM_BYTES_CHUNK_SIZE = 16

internal val seedChannel: Channel<String> = Channel(1024)

private val NonceGeneratorCoroutineName = CoroutineName("nonce-generator")

@OptIn(DelicateCoroutinesApi::class)
@Suppress("CoroutineContextWithJob")
private val nonceGeneratorJob = GlobalScope.launch(
    context = Dispatchers.Default + NonCancellable + NonceGeneratorCoroutineName,
    start = CoroutineStart.LAZY
) {
    val seedChannel = seedChannel
    var lastReseed = 0L
    // empty strings are fine, because generateNonce cares about the string length
    val randomNonces =
        Array(
            SECURE_NONCE_COUNT * NONCE_SIZE_IN_BYTES * INSECURE_NONCE_COUNT_FACTOR * 2 / RANDOM_BYTES_CHUNK_SIZE * 2
        ) {
            ""
        }
    val secureInstance = lookupSecureRandom()
    val weakRandom = SecureRandom.getInstance(SHA1PRNG)
    val weakKotlinRandom = weakRandom.asKotlinRandom() // we need a kotlin random for kotlin.collections.shuffle

    val secureBytes = ByteArray(SECURE_NONCE_COUNT * NONCE_SIZE_IN_BYTES)
    val weakBytes = ByteArray(secureBytes.size * INSECURE_NONCE_COUNT_FACTOR)

    weakRandom.setSeed(secureInstance.generateSeed(secureBytes.size))

    try {
        while (true) {
            // fill both
            secureInstance.nextBytes(secureBytes)
            weakRandom.nextBytes(weakBytes)

            // mix secure and weak
            for (i in secureBytes.indices) {
                weakBytes[i * INSECURE_NONCE_COUNT_FACTOR] = secureBytes[i]
            }

            // reseed weak bytes
            // if too much time then reseed completely
            // otherwise simply reseed with mixed
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastReseed > SECURE_RESEED_PERIOD) {
                weakRandom.setSeed(lastReseed - currentTime)
                weakRandom.setSeed(secureInstance.generateSeed(secureBytes.size))
                lastReseed = currentTime
            } else {
                weakRandom.setSeed(secureBytes)
            }

            /*
            concat entries with entries from the previous round

            realistically, hex.length here could be a constant as it *should* always be the same length
            but it's just easier to use hex.length

            we're only setting the first 1/2 elements in randomNonces not the full array,
            the remaining elements are preserved from the last iteration

            we overwrite the first half of the elements,
            as those are the ones that were previously sent to the channel
             */
            val hex = weakBytes.toHexString()
            for (index in 0..<hex.length / RANDOM_BYTES_CHUNK_SIZE) {
                val offset = index * RANDOM_BYTES_CHUNK_SIZE
                randomNonces[index] = hex.substring(offset, offset + RANDOM_BYTES_CHUNK_SIZE)
            }
            // and shuffle with weak random (reseeded)
            randomNonces.shuffle(weakKotlinRandom) // shuffle array in-place to avoid extra allocations

            // send first half to the channel
            for (index in 0 until randomNonces.size / 2) {
                seedChannel.send(randomNonces[index])
            }
        }
    } catch (t: Throwable) {
        seedChannel.close(t)
    } finally {
        seedChannel.close()
    }
}

internal fun ensureNonceGeneratorRunning() {
    nonceGeneratorJob.start()
}

private fun lookupSecureRandom(): SecureRandom {
    System.getProperty("io.ktor.random.secure.random.provider")?.let { name ->
        getInstanceOrNull(name)?.let { return it }
    }

    for (name in SECURE_RANDOM_PROVIDERS) {
        getInstanceOrNull(name)?.let { return it }
    }

    val logger = LoggerFactory.getLogger("io.ktor.util.random")

    logger.warn("None of the ${SECURE_RANDOM_PROVIDERS.joinToString()} found, falling back to the JDK strong default")

    // try JVM-determined strong instances
    // on OpenJDK, this is set to Windows-PRNG:SunMSCAPI and DRBG:SUN on Windows and NativePRNGBlocking:SUN and DRBG:SUN otherwise.
    try {
        return SecureRandom.getInstanceStrong()
    } catch (_: NoSuchAlgorithmException) {
        // ignored
    }

    logger.warn("None of the JDK determined strong SecureRandom providers were available, falling back to the default")

    // fallback (*should* never be null)
    return getInstanceOrNull() ?: error("No SecureRandom implementation found")
}

private fun getInstanceOrNull(name: String? = null) = try {
    if (name != null) {
        SecureRandom.getInstance(name)
    } else {
        SecureRandom()
    }
} catch (_: NoSuchAlgorithmException) {
    null
}
