/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwt.JWT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Token material returned or validated by the OpenID Connect plugin.
 *
 * This is the base type for all token-based principals in the OpenID Connect plugin. Use one of the concrete
 * subclasses depending on the token source:
 * - [Id] for full OpenID Connect flows with an ID token.
 * - [Access] for JWT access tokens, for example, Bearer tokens.
 * - [Opaque] for opaque access tokens handled via RFC 7662 introspection.
 *
 * The plugin creates token instances after validation. Constructors for token-bearing subclasses are internal,
 * so applications cannot accidentally fabricate a verified token principal.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcToken)
 */
@Serializable
public sealed class OidcToken {
    /**
     * Token principal from an OpenID Connect flow containing an ID token.
     *
     * @property value verified ID token value.
     * @property accessToken access token returned with the ID token.
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
    ) : OidcToken() {
        /**
         * Decoded claims from [value]. Accessing these values does not perform verification by itself.
         */
        public val claims: TokenClaims by lazy { TokenClaims(JWT.decode(value)) }

        /**
         * Decoded claims from [accessToken]. Accessing this property requires the access token to be a JWT.
         */
        public val accessTokenClaims: TokenClaims by lazy { TokenClaims(JWT.decode(accessToken)) }
    }

    /**
     * Token principal from a verified JWT access token.
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
    ) : OidcToken() {
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
     * Token principal from an opaque access token validated via RFC 7662 introspection.
     *
     * Opaque tokens cannot be decoded locally. They are accepted only when the provider configures
     * [OpaqueTokenStrategy.Introspect].
     *
     * @property value opaque access token value.
     * @property introspection normalized introspection response returned by the authorization server.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcToken.Opaque)
     */
    @Serializable
    @SerialName("opaque_token")
    public class Opaque internal constructor(
        public val value: String,
        public val introspection: OpaqueTokenIntrospection,
    ) : OidcToken()

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

    public companion object {
        /**
         * [SerializersModule] for polymorphic serialization of [OidcToken] subclasses.
         *
         * Register this module in your [kotlinx.serialization.json.Json] configuration when using custom token
         * serialization.
         */
        public val serializersModule: SerializersModule by lazy {
            SerializersModule {
                polymorphic(OidcToken::class) {
                    subclass(Id::class)
                    subclass(Access::class)
                    subclass(Opaque::class)
                }
            }
        }
    }
}
