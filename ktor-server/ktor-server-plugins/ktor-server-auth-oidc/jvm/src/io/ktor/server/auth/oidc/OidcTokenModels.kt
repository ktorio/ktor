@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Structured JWT claims access.
 *
 * Claims are decoded from an already verified token by the OpenID Connect plugin. Accessing these values does not
 * perform verification by itself.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.TokenClaims)
 */
public class TokenClaims internal constructor(private val jwt: DecodedJWT) {
    /**
     * Decoded JWT header as JSON.
     */
    public val header: JsonObject get() = parseJsonObject(jwt.header)

    /**
     * Decoded JWT payload claims as JSON.
     */
    public val payload: JsonObject get() = parseJsonObject(jwt.payload)

    /**
     * Key identifier from the JWT header.
     */
    public val keyId: String? get() = jwt.keyId

    /**
     * Type from the JWT header.
     */
    public val type: String? get() = jwt.type

    /**
     * Signing algorithm from the JWT header.
     */
    public val algorithm: String? get() = jwt.algorithm

    /**
     * Issuer claim.
     */
    public val issuer: String? get() = jwt.issuer

    /**
     * Subject claim.
     */
    public val subject: String? get() = jwt.subject

    /**
     * Audience claim values.
     */
    public val audience: List<String> get() = jwt.audience ?: emptyList()

    /**
     * Expiration time.
     */
    public val expiresAt: Instant? get() = jwt.expiresAtAsInstant?.toKotlinInstant()

    /**
     * Not-before time.
     */
    public val notBefore: Instant? get() = jwt.notBeforeAsInstant?.toKotlinInstant()

    /**
     * Issuance time.
     */
    public val issuedAt: Instant? get() = jwt.issuedAtAsInstant?.toKotlinInstant()

    /**
     * JWT ID claim.
     */
    public val jwtId: String? get() = jwt.id

    /**
     * Returns a decoded JWT payload claim by name.
     *
     * @param name claim name.
     * @return JSON claim value, or `null` when absent.
     */
    public fun claim(name: String): JsonElement? = payload[name]

    /**
     * Returns a decoded JWT payload claim as a string.
     *
     * @param name claim name.
     * @return claim string value, or `null` when absent or not a JSON string.
     */
    public fun claimString(name: String): String? =
        claim(name)?.jsonPrimitive?.takeIf { it.isString }?.contentOrNull

    /**
     * Returns a JWT header value as a string.
     *
     * @param name header name.
     * @return header string value, or `null` when absent or not a JSON string.
     */
    public fun headerString(name: String): String? =
        header[name]?.jsonPrimitive?.takeIf { it.isString }?.contentOrNull

    private fun parseJsonObject(raw: String): JsonObject {
        return runCatching {
            val decoded = Base64.withPadding(Base64.PaddingOption.ABSENT).decode(raw)
            Json.parseToJsonElement(decoded.decodeToString()) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
    }
}

/**
 * Normalized RFC 7662 token introspection response.
 *
 * @property active whether the token is currently active.
 * @property scope OAuth scope string returned by the introspection endpoint.
 * @property clientId client identifier associated with the token.
 * @property username resource owner username, when returned by the authorization server.
 * @property tokenType token type, such as `Bearer`.
 * @property expiresAt expiration time as seconds since the Unix epoch.
 * @property issuedAt issuance time as seconds since the Unix epoch.
 * @property notBefore not-before time as seconds since the Unix epoch.
 * @property subject subject identifier associated with the token.
 * @property audience normalized token audiences. String audiences are preserved as a single value.
 * @property issuer token issuer.
 * @property jwtId token identifier.
 * @property claims raw JSON claims returned by the introspection endpoint.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpaqueTokenIntrospection)
 */
@Serializable
public class OpaqueTokenIntrospection(
    public val active: Boolean,
    public val scope: String? = null,
    @SerialName("client_id")
    public val clientId: String? = null,
    public val username: String? = null,
    @SerialName("token_type")
    public val tokenType: String? = null,
    @SerialName("exp")
    public val expiresAt: Long? = null,
    @SerialName("iat")
    public val issuedAt: Long? = null,
    @SerialName("nbf")
    public val notBefore: Long? = null,
    @SerialName("sub")
    public val subject: String? = null,
    @SerialName("aud")
    public val audience: List<String> = emptyList(),
    @SerialName("iss")
    public val issuer: String? = null,
    @SerialName("jti")
    public val jwtId: String? = null,
    public val claims: JsonObject = JsonObject(emptyMap()),
)

internal fun JsonObject.toOpaqueTokenIntrospection(): OpaqueTokenIntrospection =
    OpaqueTokenIntrospection(
        active = boolean("active") ?: false,
        scope = string("scope"),
        clientId = string("client_id"),
        username = string("username"),
        tokenType = string("token_type"),
        expiresAt = long("exp"),
        issuedAt = long("iat"),
        notBefore = long("nbf"),
        subject = string("sub"),
        audience = audience("aud"),
        issuer = string("iss"),
        jwtId = string("jti"),
        claims = this,
    )

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.boolean(name: String): Boolean? =
    this[name]?.jsonPrimitive?.booleanOrNull

private fun JsonObject.long(name: String): Long? =
    this[name]?.jsonPrimitive?.longOrNull

private fun JsonObject.audience(name: String): List<String> =
    when (val value = this[name]) {
        is JsonArray -> value.mapNotNull { it.jsonPrimitive.takeIf { primitive -> primitive.isString }?.contentOrNull }
        is JsonPrimitive -> value.contentOrNull?.let(::listOf).orEmpty()
        else -> emptyList()
    }

internal fun Payload.extractUserInfo(): OidcToken.UserInfo {
    require(!subject.isNullOrBlank()) {
        "subject claim is missing from the JWT payload"
    }
    return OidcToken.UserInfo(
        subject = subject,
        name = getClaim("name").asString(),
        email = getClaim("email").asString(),
        emailVerified = getClaim("email_verified").asBoolean(),
        picture = getClaim("picture").asString(),
        givenName = getClaim("given_name").asString(),
        familyName = getClaim("family_name").asString(),
        preferredUsername = getClaim("preferred_username").asString(),
    )
}
