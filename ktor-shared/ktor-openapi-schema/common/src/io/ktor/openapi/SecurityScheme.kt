/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Defines a security scheme that can be used by the operations.
 *
 * @property type The type of the security scheme.
 * @property description A short description for a security scheme.
 */
@Serializable(SecuritySchemeSerializer::class)
public sealed interface SecurityScheme {
    public val type: SecuritySchemeType
    public val description: String?
}

@OptIn(InternalSerializationApi::class)
internal object SecuritySchemeSerializer : KSerializer<SecurityScheme> {
    @Suppress("UNCHECKED_CAST")
    private val serializers = mapOf(
        SecuritySchemeType.HTTP to HttpSecurityScheme.serializer() as KSerializer<SecurityScheme>,
        SecuritySchemeType.API_KEY to ApiKeySecurityScheme.serializer() as KSerializer<SecurityScheme>,
        SecuritySchemeType.OAUTH2 to OAuth2SecurityScheme.serializer() as KSerializer<SecurityScheme>,
        SecuritySchemeType.OPEN_ID_CONNECT to OpenIdConnectSecurityScheme.serializer() as KSerializer<SecurityScheme>,
    )

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("SecurityScheme") {
            element("type", SecuritySchemeType.serializer().descriptor)
        }

    override fun serialize(encoder: Encoder, value: SecurityScheme) {
        val serializer = serializers[value.type]
            ?: error("Unknown security scheme type: ${value.type}")
        return serializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): SecurityScheme {
        val element: GenericElement = decoder.decodeSerializableValue(
            deserializer = decoder.serializersModule.serializer()
        )
        val (_, typename) = element.entries().firstOrNull { e -> e.first == "type" }
            ?: error("SecurityScheme is missing 'type' field")
        val type = typename.deserialize(SecuritySchemeType.serializer())
        val serializer = serializers[type]
            ?: error("Unknown security scheme type: $type")
        return element.deserialize(serializer)
    }
}

/**
 * Describes an HTTP-based security scheme.
 *
 * @property type The type of the security scheme ([SecuritySchemeType.HTTP]).
 * @property scheme The name of the HTTP Authorization scheme.
 * @property bearerFormat A hint to the client to identify how the bearer token is formatted.
 * @property description A short description for a security scheme.
 * @property extensions Specification-extensions for this security scheme (keys must start with `x-`).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(HttpSecurityScheme.Companion.Serializer::class)
@KeepGeneratedSerializer
public data class HttpSecurityScheme(
    public val scheme: String? = null,
    public val bearerFormat: String? = null,
    public override val description: String? = null,
    public override val extensions: ExtensionProperties = null,
) : SecurityScheme, Extensible {

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    public override val type: SecuritySchemeType = SecuritySchemeType.HTTP

    public companion object {
        public const val DEFAULT_BASIC_DESCRIPTION: String = "HTTP Basic Authentication"
        public const val DEFAULT_BEARER_DESCRIPTION: String = "HTTP Bearer Authentication"
        public const val DEFAULT_DIGEST_DESCRIPTION: String = "HTTP Digest Authentication"
        public const val DEFAULT_JWT_DESCRIPTION: String = "JWT Bearer Authentication"

        internal object Serializer : ExtensibleMixinSerializer<HttpSecurityScheme>(
            generatedSerializer(),
            { ss, extensions -> ss.copy(extensions = extensions) }
        )
    }
}

/**
 * Describes an API Key-based security scheme for OpenAPI 3.0+ specifications.
 *
 * The API Key security scheme is used to authenticate requests using a single static API key
 * that can be passed in a header, query parameter, or cookie.
 *
 * @property name The name of the header, query, or cookie parameter containing the API key.
 * @property in The location where the API key is passed (query, header, or cookie).
 * @property type The type of the security scheme ([SecuritySchemeType.API_KEY]).
 * @property description A short description for this security scheme.
 * @property extensions Specification-extensions for this security scheme (keys must start with `x-`).
 * @see SecuritySchemeIn for available locations
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(ApiKeySecurityScheme.Companion.Serializer::class)
@KeepGeneratedSerializer
public data class ApiKeySecurityScheme(
    public val name: String? = null,
    public val `in`: SecuritySchemeIn? = null,
    public override val description: String? = null,
    public override val extensions: ExtensionProperties = null
) : SecurityScheme, Extensible {

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    public override val type: SecuritySchemeType = SecuritySchemeType.API_KEY

    public companion object {
        public const val DEFAULT_DESCRIPTION: String = "API Key Authentication"

        internal object Serializer : ExtensibleMixinSerializer<ApiKeySecurityScheme>(
            generatedSerializer(),
            { ss, extensions -> ss.copy(extensions = extensions) }
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable(OAuth2SecurityScheme.Companion.Serializer::class)
@KeepGeneratedSerializer
public data class OAuth2SecurityScheme(
    public val flows: OAuthFlows? = null,
    public override val description: String? = null,
    public override val extensions: ExtensionProperties = null,
) : SecurityScheme, Extensible {

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    public override val type: SecuritySchemeType = SecuritySchemeType.OAUTH2

    public companion object {
        public const val DEFAULT_DESCRIPTION: String = "OAuth2 Authentication"

        internal object Serializer : ExtensibleMixinSerializer<OAuth2SecurityScheme>(
            generatedSerializer(),
            { ss, extensions -> ss.copy(extensions = extensions) }
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable(OpenIdConnectSecurityScheme.Companion.Serializer::class)
@KeepGeneratedSerializer
public data class OpenIdConnectSecurityScheme(
    public val openIdConnectUrl: String? = null,
    public override val description: String? = null,
    public override val extensions: ExtensionProperties = null,
) : SecurityScheme, Extensible {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    public override val type: SecuritySchemeType = SecuritySchemeType.OPEN_ID_CONNECT

    public companion object {
        public const val DEFAULT_DESCRIPTION: String = "OpenID Connect Authentication"

        internal object Serializer : ExtensibleMixinSerializer<OpenIdConnectSecurityScheme>(
            generatedSerializer(),
            { ss, extensions -> ss.copy(extensions = extensions) }
        )
    }
}

/**
 * The type of the security scheme.
 */
@Serializable
public enum class SecuritySchemeType {
    @SerialName("apiKey")
    API_KEY,

    @SerialName("http")
    HTTP,

    @SerialName("oauth2")
    OAUTH2,

    @SerialName("openIdConnect")
    OPEN_ID_CONNECT
}

/**
 * The location of the API key for apiKey type security schemes.
 */
@Serializable
public enum class SecuritySchemeIn {
    @SerialName("query")
    QUERY,

    @SerialName("header")
    HEADER,

    @SerialName("cookie")
    COOKIE
}

/**
 * Allows configuration of the supported OAuth Flows.
 *
 * @property implicit Configuration for the OAuth Implicit flow.
 * @property password Configuration for the OAuth Resource Owner Password flow.
 * @property clientCredentials Configuration for the OAuth Client Credentials flow.
 * @property authorizationCode Configuration for the OAuth Authorization Code flow.
 * @property extensions Specification-extensions for this object (keys must start with `x-`).
 */
@Serializable(OAuthFlows.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class OAuthFlows(
    public val implicit: OAuthFlow? = null,
    public val password: OAuthFlow? = null,
    public val clientCredentials: OAuthFlow? = null,
    public val authorizationCode: OAuthFlow? = null,
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<OAuthFlows>(
            generatedSerializer(),
            { flows, extensions -> flows.copy(extensions = extensions) }
        )
    }
}

/**
 * Configuration details for a supported OAuth Flow.
 *
 * @property authorizationUrl The authorization URL to be used for this flow (oauth2 implicit and authorizationCode flows).
 * @property tokenUrl The token URL to be used for this flow (oauth2 password, clientCredentials, and authorizationCode flows).
 * @property refreshUrl The URL to be used for getting refresh tokens.
 * @property scopes The available scopes for the OAuth2 security scheme.
 * @property extensions Specification-extensions for this object (keys must start with `x-`).
 */
@Serializable(OAuthFlow.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class OAuthFlow(
    public val authorizationUrl: String? = null,
    public val tokenUrl: String? = null,
    public val refreshUrl: String? = null,
    public val scopes: Map<String, String>? = null,
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        public const val DEFAULT_SCOPE_DESCRIPTION: String = "OAuth2 scope"

        internal object Serializer : ExtensibleMixinSerializer<OAuthFlow>(
            generatedSerializer(),
            { flow, extensions -> flow.copy(extensions = extensions) }
        )
    }
}
