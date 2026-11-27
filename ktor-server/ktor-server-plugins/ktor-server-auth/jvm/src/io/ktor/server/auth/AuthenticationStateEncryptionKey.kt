/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.utils.io.*
import java.security.SecureRandom

/**
 * Symmetric key material used to encrypt the temporary state cookie of an authentication protocol.
 *
 * The key protects the in-flight state between the login redirect and the callback, for example, the OpenID Connect
 * `state`, `nonce`, and PKCE code verifier, or the identifiers of pending SAML requests.
 * Configure the same key on every node in a cluster. Use [rotating] to accept cookies encrypted by the previous key
 * while new cookies are encrypted by the current key.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationStateEncryptionKey)
 */
@ExperimentalKtorApi
@JvmInline
public value class AuthenticationStateEncryptionKey private constructor(internal val keys: List<ByteArray>) {
    init {
        require(keys.isNotEmpty()) { "AuthenticationStateEncryptionKey requires at least one key" }
        require(keys.all { it.size == KEY_SIZE }) { "Each key must be $KEY_SIZE bytes for AES-256-GCM" }
    }

    public companion object {
        /**
         * Required key size, in bytes, for AES-256-GCM.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationStateEncryptionKey.Companion.KEY_SIZE)
         */
        public const val KEY_SIZE: Int = 32

        /**
         * Creates an encryption key from the supplied 32-byte value.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationStateEncryptionKey.Companion.of)
         *
         * @param key current AES-256-GCM key. The array is copied before storage.
         * @return encryption key configuration.
         */
        public fun of(
            key: ByteArray
        ): AuthenticationStateEncryptionKey = AuthenticationStateEncryptionKey(listOf(key.copyOf()))

        /**
         * Generates a new random 32-byte AES-256-GCM key.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationStateEncryptionKey.Companion.random)
         *
         * @return encryption key configuration.
         */
        public fun random(): AuthenticationStateEncryptionKey = of(randomStateBytes(KEY_SIZE))

        /**
         * Creates a rotating encryption key set.
         *
         * New cookies are encrypted with [current]. Existing cookies encrypted with [previous] are still accepted.
         * Remove [previous] after all issued state cookies have expired.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationStateEncryptionKey.Companion.rotating)
         *
         * @param current key used for new cookies. The array is copied before storage.
         * @param previous key accepted for cookies issued before rotation. The array is copied before storage.
         * @return rotating encryption key configuration.
         */
        public fun rotating(current: ByteArray, previous: ByteArray): AuthenticationStateEncryptionKey =
            AuthenticationStateEncryptionKey(listOf(current.copyOf(), previous.copyOf()))
    }
}

internal fun randomStateBytes(size: Int): ByteArray {
    return ByteArray(size).also { bytes -> SecureRandom().nextBytes(bytes) }
}
