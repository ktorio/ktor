/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth

import io.ktor.client.request.*
import io.ktor.http.auth.*
import io.ktor.http.*

/**
 * Authentication provider interface.
 */
interface AuthProvider {
    /**
     * Wait for [HttpStatusCode.Unauthorized] to send credentials.
     */
    val sendWithoutRequest: Boolean

    /**
     * Check if current provider is applicable to the request.
     */
    fun isApplicable(auth: HttpAuthHeader): Boolean

    /**
     * Add authentication method headers and creds.
     */
    suspend fun addRequestHeaders(request: HttpRequestBuilder)
}
