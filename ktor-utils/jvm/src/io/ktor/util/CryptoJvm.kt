@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("CryptoKt")
@file:Suppress("FunctionName")

package io.ktor.util

import kotlinx.coroutines.*
import java.security.*
import java.util.*

/**
 * Create a digest function with the specified [algorithm] and [salt]
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Use getDigestFunction with non-constant salt.", level = DeprecationLevel.ERROR)
fun getDigestFunction(algorithm: String, salt: String): (String) -> ByteArray = getDigestFunction(algorithm) { salt }

/**
 * Create a digest function with the specified [algorithm] and [salt] provider.
 * @param algorithm digest algorithm name
 * @param salt a function computing a salt for a particular hash input value
 */
@KtorExperimentalAPI
fun getDigestFunction(algorithm: String, salt: (value: String) -> String): (String) -> ByteArray = { e ->
    getDigest(e, algorithm, salt)
}

private fun getDigest(text: String, algorithm: String, salt: (String) -> String): ByteArray =
    with(MessageDigest.getInstance(algorithm)) {
        update(salt(text).toByteArray())
        digest(text.toByteArray())
    }

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
@KtorExperimentalAPI
actual fun sha1(bytes: ByteArray): ByteArray = runBlocking {
    Digest("SHA1").also { it += bytes }.build()
}

/**
 * Create [Digest] from specified hash [name].
 */
@KtorExperimentalAPI
actual fun Digest(name: String): Digest = DigestImpl(MessageDigest.getInstance(name))

@KtorExperimentalAPI
private inline class DigestImpl(val delegate: MessageDigest) : Digest {
    override fun plusAssign(bytes: ByteArray) {
        delegate.update(bytes)
    }

    override fun reset() {
        delegate.reset()
    }

    override suspend fun build(): ByteArray = delegate.digest()
}

/**
 * Encode string as UTF-8 bytes
 */
@KtorExperimentalAPI
@Deprecated(
    "Will be removed in future releases",
    ReplaceWith("s.toByteArray(Charsets.UTF_8)"),
    level = DeprecationLevel.ERROR
)
fun raw(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

@Suppress("KDocMissingDocumentation", "unused")
@Deprecated("Use generateNonce() instead", level = DeprecationLevel.ERROR)
val nonceRandom: Random by lazy { SecureRandom() }

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
@KtorExperimentalAPI
@Deprecated("Use generateNonce() instead", ReplaceWith("generateNonce()"), level = DeprecationLevel.ERROR)
fun nextNonce(): String = generateNonce()

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
@KtorExperimentalAPI
actual fun generateNonce(): String {
    val nonce = seedChannel.poll()
    if (nonce != null) return nonce

    return generateNonceBlocking()
}

private fun generateNonceBlocking(): String {
    ensureNonceGeneratorRunning()
    return runBlocking {
        seedChannel.receive()
    }
}

