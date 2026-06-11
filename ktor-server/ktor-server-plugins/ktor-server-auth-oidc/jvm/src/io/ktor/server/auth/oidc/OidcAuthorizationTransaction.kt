/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.sessions.SameSite
import io.ktor.util.*

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
)

private val ApplicationCall.secureCookie: Boolean
    get() = request.origin.scheme == "https"

internal suspend fun ApplicationCall.createAuthorizationTransaction(
    stateCodec: OidcStateCodec,
    state: String,
): OidcAuthorizationTransaction {
    val nonce = generateNonceSuspend()
    val transaction = OidcAuthorizationTransaction(nonce)
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
