/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import java.security.SecureRandom

/**
 * Symmetric key material used to encrypt the temporary OAuth state cookie.
 *
 * The key protects the in-flight OAuth state between the login redirect and the callback, including the OIDC
 * `state`, `nonce`, and PKCE code verifier. Configure the same key on every node in a cluster. Use [rotating] to
 * accept cookies encrypted by the previous key while new cookies are encrypted by the current key.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcStateEncryptionKey)
 */
@JvmInline
public value class OidcStateEncryptionKey private constructor(internal val keys: List<ByteArray>) {
    init {
        require(keys.isNotEmpty()) { "OidcStateEncryptionKey requires at least one key" }
        require(keys.all { it.size == KEY_SIZE }) { "Each key must be $KEY_SIZE bytes for AES-256-GCM" }
    }

    public companion object {
        /**
         * Required key size, in bytes, for AES-256-GCM.
         */
        public const val KEY_SIZE: Int = 32

        /**
         * Creates an encryption key from the supplied 32-byte value.
         *
         * @param key current AES-256-GCM key. The array is copied before storage.
         * @return encryption key configuration.
         */
        public fun of(key: ByteArray): OidcStateEncryptionKey = OidcStateEncryptionKey(listOf(key.copyOf()))

        /**
         * Generates a new random 32-byte AES-256-GCM key.
         *
         * @return encryption key configuration.
         */
        public fun random(): OidcStateEncryptionKey = of(randomBytes(KEY_SIZE))

        /**
         * Creates a rotating encryption key set.
         *
         * New cookies are encrypted with [current]. Existing cookies encrypted with [previous] are still accepted.
         * Remove [previous] after all issued OAuth state cookies have expired.
         *
         * @param current key used for new cookies. The array is copied before storage.
         * @param previous key accepted for cookies issued before rotation. The array is copied before storage.
         * @return rotating encryption key configuration.
         */
        public fun rotating(current: ByteArray, previous: ByteArray): OidcStateEncryptionKey =
            OidcStateEncryptionKey(listOf(current.copyOf(), previous.copyOf()))
    }
}

internal fun randomBytes(size: Int): ByteArray {
    return ByteArray(size).also { bytes -> SecureRandom().nextBytes(bytes) }
}
