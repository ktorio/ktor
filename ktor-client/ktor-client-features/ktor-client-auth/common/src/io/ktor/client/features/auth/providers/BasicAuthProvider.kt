/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.providers

import io.ktor.client.features.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

/**
 * Add [BasicAuthProvider] to client [Auth] providers.
 */
public fun Auth.basic(block: BasicAuthConfig.() -> Unit) {
    with(BasicAuthConfig().apply(block)) {
        providers.add(BasicAuthProvider(_credentials, realm, _sendWithoutRequest))
    }
}

/**
 * [BasicAuthProvider] configuration.
 */
public class BasicAuthConfig {
    /**
     * Required: The username of the basic auth.
     */
    @Deprecated("Please use `credentials {}` function instead")
    public lateinit var username: String

    /**
     * Required: The password of the basic auth.
     */
    @Deprecated("Please use `credentials {}` function instead")
    public lateinit var password: String

    /**
     * Send credentials in without waiting for [HttpStatusCode.Unauthorized].
     */
    @Deprecated("Please use `sendWithoutRequest {}` function instead")
    public var sendWithoutRequest: Boolean = false

    /**
     * Optional: current provider realm
     */
    public var realm: String? = null

    internal var _sendWithoutRequest: (HttpRequestBuilder) -> Boolean = { sendWithoutRequest }

    internal var _credentials: suspend () -> BasicAuthCredentials? = {
        BasicAuthCredentials(username = username, password = password)
    }

    /**
     * Send credentials in without waiting for [HttpStatusCode.Unauthorized].
     */
    public fun sendWithoutRequest(block: (HttpRequestBuilder) -> Boolean) {
        _sendWithoutRequest = block
    }

    /**
     * Required: Credentials provider.
     */
    public fun credentials(block: suspend () -> BasicAuthCredentials?) {
        _credentials = block
    }
}

/**
 * Credentials for [BasicAuthProvider].
 */
public class BasicAuthCredentials(
    public val username: String,
    public val password: String
)

/**
 * Client basic authentication provider.
 */
public class BasicAuthProvider(
    private val credentials: suspend () -> BasicAuthCredentials?,
    private val realm: String? = null,
    private val sendWithoutRequestCallback: (HttpRequestBuilder) -> Boolean = { false }
) : AuthProvider {

    @Deprecated("Consider using constructor with credentials provider instead")
    public constructor(
        username: String,
        password: String,
        realm: String? = null,
        sendWithoutRequest: Boolean = false
    ) : this(
        credentials = { BasicAuthCredentials(username, password) },
        realm = realm,
        sendWithoutRequestCallback = { sendWithoutRequest }
    )

    private val tokensHolder = AuthTokenHolder(credentials)

    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = sendWithoutRequestCallback(request)

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth.authScheme != AuthScheme.Basic) return false

        if (realm != null) {
            if (auth !is HttpAuthHeader.Parameterized) return false
            return auth.parameter("realm") == realm
        }

        return true
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder) {
        val credentials = tokensHolder.loadToken() ?: return
        request.headers[HttpHeaders.Authorization] = constructBasicAuthValue(credentials)
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        tokensHolder.setToken(credentials)
        return true
    }
}

internal fun constructBasicAuthValue(credentials: BasicAuthCredentials): String {
    val authString = "${credentials.username}:${credentials.password}"
    val authBuf = authString.toByteArray(Charsets.UTF_8).encodeBase64()

    return "Basic $authBuf"
}
