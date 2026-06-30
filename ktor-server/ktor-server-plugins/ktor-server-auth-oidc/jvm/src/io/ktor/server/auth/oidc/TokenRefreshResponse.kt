/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
internal data class TokenRefreshResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("id_token")
    val idToken: String? = null,
    val scope: String? = null,
)

/**
 * Token response returned by [refreshToken].
 *
 * Contains raw token response fields, so applications that manage their own tokens can decide how to
 * persist, rotate, or expose token material.
 *
 * @property accessToken Access token returned by the token endpoint.
 * @property refreshToken Raw refresh token returned by the token endpoint, or `null` when the provider
 *   did not rotate or return one. Persist [refreshToken] when it is present; otherwise keep the refresh token
 *   used for the request.
 * @property expiresIn Token lifetime as [Duration], or `null` when unavailable.
 * @property tokenType Token type returned by the token endpoint.
 * @property scope Scope string returned by the token endpoint, or `null` when unavailable.
 * @property idToken Verified ID-token when [idToken] is present, otherwise `null`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcTokenRefreshResult)
 */
public class OidcTokenRefreshResult(
    public val accessToken: String,
    public val refreshToken: String? = null,
    public val expiresIn: Duration? = null,
    public val tokenType: String,
    public val scope: String? = null,
    public val idToken: OidcToken.Id? = null,
)
