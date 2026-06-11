/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.sessions.SameSite
import io.ktor.util.*
import kotlin.io.encoding.Base64

private const val PkceCodeVerifierLength: Int = 64
internal const val PkceCodeChallengeMethod: String = "S256"

private val CookieMaxAgeSeconds = AuthorizationTransactionTtl.inWholeSeconds.toInt()
internal const val OidcStateCookieName = "KTOR_OIDC_STATE"

internal fun String.toOidcStateCookie(secure: Boolean, maxAge: Int = CookieMaxAgeSeconds): Cookie =
    Cookie(
        name = OidcStateCookieName,
        value = this,
        maxAge = maxAge,
        path = "/",
        httpOnly = true,
        secure = secure,
        extensions = mapOf("SameSite" to SameSite.Lax),
    )

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

private val ApplicationCall.secureCookie: Boolean
    get() = request.origin.scheme == "https"

internal suspend fun ApplicationCall.createAuthorizationTransaction(
    stateCodec: OidcStateCodec,
    state: String,
): OidcAuthorizationTransaction {
    val nonce = generateNonceSuspend()
    val codeVerifier = generateNonceSuspend(length = PkceCodeVerifierLength)
    val transaction = OidcAuthorizationTransaction(nonce, codeVerifier)
    val cookie = stateCodec.encode(state, transaction).toOidcStateCookie(secureCookie)
    response.cookies.append(cookie)
    return transaction
}

internal fun ApplicationCall.consumeAuthorizationTransaction(
    stateCodec: OidcStateCodec,
    state: String,
): OidcAuthorizationTransaction? {
    val transaction = readAuthorizationTransaction(stateCodec, state) ?: return null
    response.cookies.append("".toOidcStateCookie(secureCookie, maxAge = 0))
    return transaction
}

internal fun ApplicationCall.readAuthorizationTransaction(
    stateCodec: OidcStateCodec,
    state: String,
): OidcAuthorizationTransaction? =
    request.cookies[OidcStateCookieName]?.let { stateCodec.decode(value = it, state) }
