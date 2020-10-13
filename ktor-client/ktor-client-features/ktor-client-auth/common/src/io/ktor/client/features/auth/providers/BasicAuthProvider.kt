/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.providers

import io.ktor.client.features.auth.*
import io.ktor.client.request.*
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
        providers.add(BasicAuthProvider(username, password, realm, sendWithoutRequest))
    }
}

/**
 * [BasicAuthProvider] configuration.
 */
public class BasicAuthConfig {
    /**
     * Required: The username of the basic auth.
     */
    public lateinit var username: String

    /**
     * Required: The password of the basic auth.
     */
    public lateinit var password: String

    /**
     * Optional: current provider realm
     */
    public var realm: String? = null

    /**
     * Send credentials in without waiting for [HttpStatusCode.Unauthorized].
     */
    public var sendWithoutRequest: Boolean = false
}

/**
 * Client basic authentication provider.
 */
public class BasicAuthProvider(
    private val username: String,
    private val password: String,
    private val realm: String? = null,
    override val sendWithoutRequest: Boolean = false
) : AuthProvider {
    private val defaultCharset = Charsets.UTF_8

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth.authScheme != AuthScheme.Basic) return false

        if (realm != null) {
            if (auth !is HttpAuthHeader.Parameterized) return false
            return auth.parameter("realm") == realm
        }

        return true
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder) {
        request.headers[HttpHeaders.Authorization] = constructBasicAuthValue()
    }

    internal fun constructBasicAuthValue(): String {
        val authString = "$username:$password"
        val authBuf = authString.toByteArray(defaultCharset).encodeBase64()

        return "Basic $authBuf"
    }
}
