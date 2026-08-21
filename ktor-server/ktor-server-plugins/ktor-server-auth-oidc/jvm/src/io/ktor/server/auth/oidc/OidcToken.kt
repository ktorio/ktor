/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwt.JWT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Token material returned or validated by the OpenID Connect plugin.
 *
 * This is the base type for all token-based principals in the OpenID Connect plugin. Use one of the concrete
 * subclasses depending on the token source:
 * - [Id] for full OpenID Connect flows with an ID token.
 * - [Access] for JWT access tokens verified locally, for example, JWT Bearer authentication.
 * - [Introspected] for access tokens validated via RFC 7662 introspection.
 *
 * The plugin creates token instances after validation. Constructors for token-bearing subclasses are internal,
 * so applications cannot accidentally fabricate a verified token principal.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcToken)
 */
public interface OidcToken {
    /**
     * Token principal from an OpenID Connect login containing a verified ID token plus the accompanying OAuth
     * token material.
     *
     * This is the session and OAuth-callback token type. The accompanying [accessToken] string is the provider-issued
     * access token from the token endpoint; it is not verified as a resource-server Bearer principal and is never
     * exposed as [Access]. Use [jwtBearer][OidcProvider.jwtBearer] to obtain an [Access] principal.
     *
     * @property value verified ID token value.
     * @property accessToken the accompanying access token returned with the ID token. May be JWT or opaque; it is not
     * validated against Bearer resource audiences.
     * @property refreshToken refresh token returned by the token endpoint, or `null` when unavailable.
     * @property userInfo normalized user claims extracted from the ID token or UserInfo endpoint.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcToken.Id)
     */
    @Serializable
    @SerialName("id_token")
    public class Id internal constructor(
        public val value: String,
        public val accessToken: String,
        public val refreshToken: String? = null,
        public val userInfo: UserInfo,
    ) : OidcToken {
        /**
         * Decoded claims from [value]. Accessing these values does not perform verification by itself.
         */
        public val claims: TokenClaims by lazy { TokenClaims(JWT.decode(value)) }
    }

    /**
     * Token principal from a JWT access token verified locally against this resource's [OidcBearerConfig.audience].
     *
     * This type is produced only by [OidcProvider.jwtBearer]. OAuth login does not create [Access] principals;
     * OAuth-issued access-token material remains on [Id.accessToken].
     *
     * @property value verified JWT access token value.
     * @property userInfo normalized user claims extracted from the token, or `null` when unavailable.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcToken.Access)
     */
    @Serializable
    @SerialName("access_token")
    public class Access internal constructor(
        public val value: String,
        public val userInfo: UserInfo? = null,
    ) : OidcToken {
        /**
         * Decoded claims from [value]. Accessing these values does not perform verification by itself.
         */
        public val claims: TokenClaims by lazy { TokenClaims(JWT.decode(value)) }

        /**
         * Authorized party or client identifier from the JWT access token.
         *
         * The plugin checks the standard OpenID Connect `azp` claim first, then falls back to the OAuth `client_id`
         * claim used by some providers. Returns `null` when neither claim is present.
         */
        public val clientId: String? get() = claims.claimString("azp") ?: claims.claimString("client_id")
    }

    /**
     * Token principal from an access token validated via RFC 7662 introspection.
     *
     * Introspection accepts both opaque tokens and JWT-formatted access tokens. Configure
     * `bearer { introspection { } }` to enable the introspection Bearer scheme.
     *
     * @property value access token value presented to the resource server.
     * @property introspection normalized introspection response returned by the authorization server.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcToken.Introspected)
     */
    @Serializable
    @SerialName("introspected_token")
    public class Introspected internal constructor(
        public val value: String,
        public val introspection: TokenIntrospection,
    ) : OidcToken

    /**
     * Standard user claims extracted from an ID token payload, JWT access token payload, or UserInfo response.
     *
     * @property subject subject identifier. Must not be blank.
     * @property name display name.
     * @property email email address.
     * @property emailVerified whether the provider has verified the email address.
     * @property picture profile picture URL.
     * @property givenName given name.
     * @property familyName family name.
     * @property preferredUsername preferred username.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcToken.UserInfo)
     */
    @Serializable
    public class UserInfo(
        @SerialName("sub") public val subject: String,
        public val name: String? = null,
        public val email: String? = null,
        @SerialName("email_verified") public val emailVerified: Boolean? = null,
        public val picture: String? = null,
        @SerialName("given_name") public val givenName: String? = null,
        @SerialName("family_name") public val familyName: String? = null,
        @SerialName("preferred_username") public val preferredUsername: String? = null,
    ) {
        init {
            require(subject.isNotBlank()) { "subject must not be blank" }
        }
    }
}
