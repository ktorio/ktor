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
 * Authentication provider interface.
 */
public interface AuthProvider {
    /**
     * Wait for [HttpStatusCode.Unauthorized] to send credentials.
     */
    @Deprecated("Please use sendWithoutRequest function instead")
    public val sendWithoutRequest: Boolean

    @Suppress("DEPRECATION")
    public fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = sendWithoutRequest

    /**
     * Checks if current provider is applicable to the request.
     */
    public fun isApplicable(auth: HttpAuthHeader): Boolean

    /**
     * Adds authentication method headers and credentials.
     * @param authHeader value of `WWW-Authenticate` header from failed response, if exists
     * @param request builder for an authenticated request
     */
    public suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader? = null)

    /**
     * Refresh token if required.
     *
     * @param call - response triggered token refresh.
     * @return if the token was successfully refreshed.
     */
    public suspend fun refreshToken(response: HttpResponse): Boolean = true
}
