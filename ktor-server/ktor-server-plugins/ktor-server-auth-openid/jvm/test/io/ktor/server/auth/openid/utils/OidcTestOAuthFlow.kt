/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid.utils

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.*

internal data class PreparedLogin(
    val state: String,
    val nonce: String?,
    val authorizationSessionCookie: String,
    val authorizeUrl: Url,
)

internal suspend fun HttpClient.prepareOidcLogin(
    providerName: String = "auth0",
    configureRequest: HttpRequestBuilder.() -> Unit = {},
): PreparedLogin {
    val login = get("/oidc/$providerName/login", configureRequest)
    assertEquals(HttpStatusCode.Found, login.status)
    val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
    return PreparedLogin(
        state = assertNotNull(authorizeUrl.parameters["state"]),
        nonce = authorizeUrl.parameters["nonce"],
        authorizationSessionCookie = assertNotNull(login.oidcAuthorizationSessionCookieHeader(providerName)),
        authorizeUrl = authorizeUrl,
    )
}

internal suspend fun HttpClient.completeOidcCallback(
    login: PreparedLogin,
    providerName: String = "auth0",
    issuer: String? = null,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    val issuerQuery = issuer?.let { "&iss=$it" }.orEmpty()
    return get("/oidc/$providerName/callback?code=login-code&state=${login.state}$issuerQuery") {
        header(HttpHeaders.Cookie, login.authorizationSessionCookie)
        configureRequest()
    }
}

internal suspend fun HttpClient.signInWithIdToken(
    idTokensByState: MutableMap<String, String>,
    keys: OpenIdTestKeys,
    providerName: String = "auth0",
    subject: String = "session-user",
    callbackExpectedStatus: HttpStatusCode = HttpStatusCode.OK,
    configureToken: (PreparedLogin) -> String = { login ->
        keys.token(audience = "client-id", subject = subject, nonce = login.nonce)
    },
): String {
    val login = prepareOidcLogin(providerName)
    idTokensByState[login.state] = configureToken(login)

    val callback = completeOidcCallback(login, providerName)
    assertEquals(callbackExpectedStatus, callback.status)
    return assertNotNull(callback.oidcSessionCookieHeader())
}

internal fun assertValidPkceCodeVerifier(codeVerifier: String) {
    assertTrue(
        codeVerifier.length in 43..128,
        "PKCE code verifier must contain 43 to 128 characters",
    )
    assertTrue(
        codeVerifier.all { it.isLetterOrDigit() || it in "-._~" },
        "PKCE code verifier must use RFC 7636 unreserved characters",
    )
}
