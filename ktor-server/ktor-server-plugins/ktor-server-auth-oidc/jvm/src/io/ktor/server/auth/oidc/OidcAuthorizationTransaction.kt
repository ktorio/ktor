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

private val CookieMaxAgeSeconds = AuthorizationTransactionTtl.inWholeSeconds.toInt()
internal const val OidcStateCookieName = "KTOR_OIDC_STATE"

internal fun createOidcStateCookie(value: String, secure: Boolean, maxAge: Int = CookieMaxAgeSeconds): Cookie =
    Cookie(
        name = OidcStateCookieName,
        value = value,
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
    method: CodeChallengeMethod,
    state: String,
): OidcAuthorizationTransaction {
    require(method is CodeChallengeMethod.S256) { "Only S256 code challenge method is supported" }
    val nonce = generateNonceSuspend()
    val codeVerifier = generateNonceSuspend(CodeChallengeMethod.S256.VERIFIER_LENGTH)
    val transaction = OidcAuthorizationTransaction(nonce, codeVerifier)
    val cookie = createOidcStateCookie(value = stateCodec.encode(state, transaction), secure = secureCookie)
    response.cookies.append(cookie)
    return transaction
}

internal fun ApplicationCall.consumeAuthorizationTransaction(
    stateCodec: OidcStateCodec,
    state: String,
): OidcAuthorizationTransaction? {
    val transaction = readAuthorizationTransaction(stateCodec, state) ?: return null
    response.cookies.append(createOidcStateCookie(value = "", secure = secureCookie, maxAge = 0))
    return transaction
}

internal fun ApplicationCall.readAuthorizationTransaction(
    stateCodec: OidcStateCodec,
    state: String,
): OidcAuthorizationTransaction? =
    request.cookies[OidcStateCookieName]?.let { stateCodec.decode(value = it, state) }
