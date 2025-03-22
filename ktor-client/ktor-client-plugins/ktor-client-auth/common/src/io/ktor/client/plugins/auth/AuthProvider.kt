/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*

/**
 * An authentication provider.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.AuthProvider)
 */
public interface AuthProvider {
    /**
     * Waits for [HttpStatusCode.Unauthorized] to send credentials.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.AuthProvider.sendWithoutRequest)
     */
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    public val sendWithoutRequest: Boolean

    @Suppress("DEPRECATION_ERROR")
    public fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = sendWithoutRequest

    /**
     * Checks if the current provider is applicable to a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.AuthProvider.isApplicable)
     */
    public fun isApplicable(auth: HttpAuthHeader): Boolean

    /**
     * Adds an authentication method headers and credentials.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.AuthProvider.addRequestHeaders)
     *
     * @param authHeader value of `WWW-Authenticate` header from failed response, if exists
     * @param request builder for an authenticated request
     */
    public suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader? = null)

    /**
     * Refreshes a token if required.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.AuthProvider.refreshToken)
     *
     * @param call - response triggered token refresh.
     * @return if the token was successfully refreshed.
     */
    public suspend fun refreshToken(response: HttpResponse): Boolean = true
}
