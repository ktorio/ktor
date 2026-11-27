/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class, InternalAPI::class)

package io.ktor.server.auth.oidc

import io.ktor.server.auth.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal val AuthorizationTransactionTtl = 10.minutes

internal class OidcStateCodec(
    encryptionKey: AuthenticationStateEncryptionKey,
    private val clock: Clock = Clock.System,
) {
    private val codec = AuthenticationStateCodec(encryptionKey, OidcStateCookiePayload.serializer())

    fun encode(state: String, transaction: OidcAuthorizationTransaction): String {
        val payload = OidcStateCookiePayload(
            state = state,
            nonce = transaction.nonce,
            codeVerifier = transaction.codeVerifier,
            expiresAt = clock.now() + AuthorizationTransactionTtl,
        )
        return codec.encode(payload)
    }

    fun decode(value: String, state: String): OidcAuthorizationTransaction? {
        val payload = codec.decode(value) ?: return null
        if (payload.state != state || payload.expiresAt <= clock.now()) {
            return null
        }
        return OidcAuthorizationTransaction(payload.nonce, payload.codeVerifier)
    }
}

@Serializable
internal class OidcStateCookiePayload(
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val expiresAt: Instant,
)
