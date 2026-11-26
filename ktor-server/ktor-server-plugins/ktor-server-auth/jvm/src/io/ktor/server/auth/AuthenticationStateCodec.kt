/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth

import io.ktor.utils.io.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

private const val IV_SIZE: Int = 12
private const val TAG_SIZE_BITS: Int = 128
private const val TRANSFORMATION: String = "AES/GCM/NoPadding"

private val json = Json { ignoreUnknownKeys = true }
private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/**
 * Encrypts the in-flight state of an authentication protocol into a cookie-safe string and reads it back.
 *
 * The state is serialized to JSON and encrypted with AES-256-GCM, so the client can neither read nor modify it.
 * The codec does not expire the state: store an expiration time in [T] and check it after [decode].
 *
 * @param T the state type.
 * @param encryptionKey keys used to encrypt and decrypt the state.
 * @param serializer serializer of the state.
 */
@InternalAPI
public class AuthenticationStateCodec<T>(
    private val encryptionKey: AuthenticationStateEncryptionKey,
    private val serializer: KSerializer<T>,
) {
    /**
     * Encrypts [state] with the current key.
     *
     * @return URL-safe Base64 text without padding that contains the initialization vector and the ciphertext.
     */
    public fun encode(state: T): String {
        val iv = randomStateBytes(IV_SIZE)
        val input = json.encodeToString(serializer, state).toByteArray()
        val cipher = createCipher(Cipher.ENCRYPT_MODE, encryptionKey.keys.first(), iv)
        val ciphertext = cipher.doFinal(input)
        return base64.encode(iv + ciphertext)
    }

    /**
     * Decrypts a value produced by [encode] with any of the configured keys.
     *
     * @return the state, or `null` when [value] is malformed, was modified, or was encrypted with another key.
     */
    public fun decode(value: String): T? {
        val bytes = runCatching { base64.decode(value) }.getOrElse { return null }
        if (bytes.size <= IV_SIZE) {
            return null
        }
        val iv = bytes.copyOfRange(0, IV_SIZE)
        val ciphertext = bytes.copyOfRange(IV_SIZE, bytes.size)
        for (key in encryptionKey.keys) {
            return runCatching {
                val cipher = createCipher(Cipher.DECRYPT_MODE, key, iv)
                json.decodeFromString(serializer, cipher.doFinal(ciphertext).decodeToString())
            }.getOrElse { continue }
        }
        return null
    }

    private fun createCipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher
    }
}
