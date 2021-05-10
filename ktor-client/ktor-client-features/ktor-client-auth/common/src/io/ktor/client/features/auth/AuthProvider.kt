/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth

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

    public fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = sendWithoutRequest

    /**
     * Check if current provider is applicable to the request.
     */
    public fun isApplicable(auth: HttpAuthHeader): Boolean

    /**
     * Add authentication method headers and creds.
     */
    public suspend fun addRequestHeaders(request: HttpRequestBuilder)

    /**
     * Refresh token if required.
     *
     * @param call - response triggered token refresh.
     * @return if the token was successfully refreshed.
     */
    public suspend fun refreshToken(response: HttpResponse): Boolean = true
}
