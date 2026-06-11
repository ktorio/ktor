/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal val AuthorizationTransactionTtl = 10.minutes

internal class OidcStateCodec(
    private val encryptionKey: OidcStateEncryptionKey,
    private val clock: Clock = Clock.System,
    private val ivGenerator: () -> ByteArray = { randomBytes(IV_SIZE) },
) {
    fun encode(state: String, transaction: OidcAuthorizationTransaction): String {
        val payload = OidcStateCookiePayload(
            state = state,
            nonce = transaction.nonce,
            expiresAt = clock.now() + AuthorizationTransactionTtl,
        )
        return encrypt(payload)
    }

    fun decode(value: String, state: String): OidcAuthorizationTransaction? {
        val payload = decrypt(value) ?: return null
        if (payload.state != state || payload.expiresAt <= clock.now()) {
            return null
        }
        return OidcAuthorizationTransaction(payload.nonce)
    }

    private fun encrypt(payload: OidcStateCookiePayload): String {
        val iv = ivGenerator()
        val key = encryptionKey.keys.first()
        val cipher = createCipher(Cipher.ENCRYPT_MODE, key, iv)
        val input = json.encodeToString(payload).toByteArray()
        val ciphertext = cipher.doFinal(input)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(iv + ciphertext)
    }

    private fun decrypt(value: String): OidcStateCookiePayload? {
        val bytes = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(value)
        }.getOrElse { return null }
        if (bytes.size <= IV_SIZE) {
            return null
        }
        val iv = bytes.copyOfRange(0, IV_SIZE)
        val ciphertext = bytes.copyOfRange(IV_SIZE, bytes.size)
        for (key in encryptionKey.keys) {
            val payload = runCatching {
                val cipher = createCipher(Cipher.DECRYPT_MODE, key, iv)
                val plaintext = cipher.doFinal(ciphertext).decodeToString()
                json.decodeFromString<OidcStateCookiePayload>(plaintext)
            }.getOrElse { continue }
            return payload
        }
        return null
    }

    private fun createCipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher
    }

    private companion object {
        const val IV_SIZE: Int = 12
        const val TAG_SIZE_BITS: Int = 128
        const val TRANSFORMATION: String = "AES/GCM/NoPadding"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal class OidcStateCookiePayload(
    val state: String,
    val nonce: String,
    val expiresAt: Instant,
)
