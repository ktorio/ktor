/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

/**
 * Installs the client's [BasicAuthProvider].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.basic)
 */
@KtorDsl
public fun AuthConfig.basic(block: BasicAuthConfig.() -> Unit) {
    with(BasicAuthConfig().apply(block)) {
        this@basic.providers.add(BasicAuthProvider(credentials, realm, _sendWithoutRequest))
    }
}

/**
 * A configuration for [BasicAuthProvider].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthConfig)
 */
@KtorDsl
public class BasicAuthConfig {
    /**
     * Required: The username of the basic auth.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthConfig.username)
     */
    @Deprecated("Please use `credentials {}` function instead", level = DeprecationLevel.ERROR)
    public lateinit var username: String

    /**
     * Required: The password of the basic auth.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthConfig.password)
     */
    @Deprecated("Please use `credentials {}` function instead", level = DeprecationLevel.ERROR)
    public lateinit var password: String

    /**
     * Send credentials in without waiting for [HttpStatusCode.Unauthorized].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthConfig.sendWithoutRequest)
     */
    @Deprecated("Please use `sendWithoutRequest {}` function instead", level = DeprecationLevel.ERROR)
    public var sendWithoutRequest: Boolean = false

    /**
     * (Optional) Specifies the realm of the current provider.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthConfig.realm)
     */
    public var realm: String? = null

    @Suppress("DEPRECATION_ERROR", "PropertyName")
    internal var _sendWithoutRequest: (HttpRequestBuilder) -> Boolean = { sendWithoutRequest }

    @Suppress("DEPRECATION_ERROR")
    internal var credentials: suspend () -> BasicAuthCredentials? = {
        BasicAuthCredentials(username = username, password = password)
    }

    /**
     * Sends credentials without waiting for [HttpStatusCode.Unauthorized].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthConfig.sendWithoutRequest)
     */
    public fun sendWithoutRequest(block: (HttpRequestBuilder) -> Boolean) {
        _sendWithoutRequest = block
    }

    /**
     * Allows you to specify authentication credentials.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthConfig.credentials)
     */
    public fun credentials(block: suspend () -> BasicAuthCredentials?) {
        credentials = block
    }
}

/**
 * Contains credentials for [BasicAuthProvider].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthCredentials)
 */
public class BasicAuthCredentials(
    public val username: String,
    public val password: String
)

/**
 * An authentication provider for the Basic HTTP authentication scheme.
 * The Basic authentication scheme can be used for logging in users.
 *
 * You can learn more from [Basic authentication](https://ktor.io/docs/basic-client.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthProvider)
 */
public class BasicAuthProvider(
    private val credentials: suspend () -> BasicAuthCredentials?,
    private val realm: String? = null,
    private val sendWithoutRequestCallback: (HttpRequestBuilder) -> Boolean = { false }
) : AuthProvider {

    @Deprecated("Consider using constructor with credentials provider instead", level = DeprecationLevel.ERROR)
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

    @Suppress("OverridingDeprecatedMember")
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = sendWithoutRequestCallback(request)

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (!AuthScheme.Basic.equals(auth.authScheme, ignoreCase = true)) {
            LOGGER.trace("Basic Auth Provider is not applicable for $auth")
            return false
        }

        val isSameRealm = when {
            realm == null -> true
            auth !is HttpAuthHeader.Parameterized -> false
            else -> auth.parameter("realm") == realm
        }
        if (!isSameRealm) {
            LOGGER.trace("Basic Auth Provider is not applicable for this realm")
        }
        return isSameRealm
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        val credentials = tokensHolder.loadToken() ?: return
        request.headers[HttpHeaders.Authorization] = constructBasicAuthValue(credentials)
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        tokensHolder.setToken(credentials)
        return true
    }

    /**
     * Clears the currently stored authentication tokens from the cache.
     *
     * This method should be called in the following cases:
     * - When the credentials have been updated and need to take effect
     * - When you want to force re-authentication
     * - When you want to clear sensitive authentication data
     *
     * Note: The result of [credentials] invocation is cached internally.
     * Calling this method will force the next authentication attempt to fetch fresh credentials.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BasicAuthProvider.clearToken)
     */
    @InternalAPI // TODO KTOR-8180: Provide control over tokens to user code
    public fun clearToken() {
        tokensHolder.clearToken()
    }
}

internal fun constructBasicAuthValue(credentials: BasicAuthCredentials): String {
    val authString = "${credentials.username}:${credentials.password}"
    val authBuf = authString.toByteArray(Charsets.UTF_8).encodeBase64()

    return "Basic $authBuf"
}
