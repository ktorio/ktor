/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.server.auth.oidc

import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.io.encoding.Base64

internal const val OidcStateCookiePrefix = "KTOR_OIDC_STATE"

/**
 * Each provider uses its own state cookie, so a login started with one provider does not overwrite
 * the pending authorization transaction of another. Provider names are validated at registration
 * to lowercase letters, digits, and hyphens, so the name is always cookie-safe.
 */
internal fun oidcStateCookieName(providerName: String): String =
    "${OidcStateCookiePrefix}_${providerName.uppercase()}"

internal class OidcAuthorizationTransaction(
    val nonce: String,
    val codeVerifier: String,
) {
    fun codeChallenge(): String {
        val digester = DigestAlgorithm.SHA_256.toDigester()
        val digest = digester.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(digest)
    }
}

private val PendingAuthorizationTransactionKey =
    AttributeKey<OidcAuthorizationTransaction>("OidcPendingAuthorizationTransaction")

internal suspend fun ApplicationCall.createAuthorizationTransaction(
    cookie: AuthenticationStateCookie,
    stateCodec: OidcStateCodec,
    method: CodeChallengeMethod,
    state: String,
): OidcAuthorizationTransaction {
    check(method is CodeChallengeMethod.S256) { "Custom code challenge methods are not supported" }
    val nonce = generateNonceSuspend()
    val codeVerifier = generateNonceSuspend(CodeChallengeMethod.S256.VERIFIER_LENGTH)
    val transaction = OidcAuthorizationTransaction(nonce, codeVerifier)
    attributes.put(PendingAuthorizationTransactionKey, transaction)
    cookie.write(this, stateCodec.encode(state, transaction))
    return transaction
}

internal fun ApplicationCall.consumeAuthorizationTransaction(
    cookie: AuthenticationStateCookie,
    stateCodec: OidcStateCodec,
    state: String,
): OidcAuthorizationTransaction? {
    val transaction = readAuthorizationTransaction(cookie, stateCodec, state) ?: return null
    attributes.remove(PendingAuthorizationTransactionKey)
    cookie.clear(this)
    return transaction
}

internal fun ApplicationCall.readAuthorizationTransaction(
    cookie: AuthenticationStateCookie,
    stateCodec: OidcStateCodec,
    state: String,
): OidcAuthorizationTransaction? =
    attributes.getOrNull(PendingAuthorizationTransactionKey)
        ?: cookie.read(this)?.let { stateCodec.decode(value = it, state) }
