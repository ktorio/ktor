/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.auth.*
import io.ktor.utils.io.*
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import java.time.Instant as JavaInstant

/**
 * Local signing keys for OpenID Connect tests.
 *
 * Use this utility to issue JWT access tokens and ID tokens that pass normal OIDC validation without contacting a real
 * discovery endpoint or JWKS endpoint. Pair it with static [OpenIdProviderMetadata] and
 * [OidcProviderConfig.jwt] to keep issuer, audience, algorithm, and signature checks enabled in tests:
 * ```kotlin
 * val keys = OpenIdTestKeys.rsa(issuer = TEST_ISSUER, audience = TEST_AUDIENCE)
 *
 * val provider = oidc.provider("test") {
 *     issuer = TEST_ISSUER
 *     metadata = OpenIdProviderMetadata(
 *         issuer = TEST_ISSUER,
 *         authorizationEndpoint = "$TEST_ISSUER/authorize",
 *         tokenEndpoint = "$TEST_ISSUER/token",
 *         jwksUri = "$TEST_ISSUER/jwks",
 *     )
 *     jwt(keys)
 *     accessToken { audiences = setOf(TEST_AUDIENCE) }
 *     bearer()
 * }
 *
 * val token = keys.accessToken { subject = "user-1" }
 *
 * val idToken = keys.idToken(subject = "user-1") { audience = "client-id" }
 * ```
 *
 * The generated [jwkProvider] exposes only the public key. Token helpers sign with the matching private key.
 *
 * HMAC algorithms are intentionally unsupported.
 *
 * @property keyId key ID written to token headers and exposed by the generated JWK.
 * @property algorithm signing algorithm used by the public token helpers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdTestKeys)
 */
public class OpenIdTestKeys internal constructor(
    public val keyId: String,
    public val algorithm: SignatureAlgorithm,
    private val keyPair: KeyPair,
    internal val defaultIssuer: String? = null,
    internal val defaultAudience: String? = null,
) {
    internal val jwk: Jwk = run {
        val map = when (val publicKey = keyPair.public) {
            is RSAPublicKey -> mutableMapOf<String, Any>(
                "kid" to keyId,
                "kty" to "RSA",
                "use" to "sig",
                "n" to publicKey.modulus.toUnsignedBase64Url(),
                "e" to publicKey.publicExponent.toUnsignedBase64Url(),
                "alg" to checkNotNull(algorithm.jwaName)
            )

            is ECPublicKey -> mapOf(
                "kid" to keyId,
                "kty" to "EC",
                "use" to "sig",
                "alg" to checkNotNull(algorithm.jwaName),
                "crv" to algorithm.ecJwaCurve,
                "x" to publicKey.w.affineX.toUnsignedBase64Url(algorithm.ecCoordinateSize),
                "y" to publicKey.w.affineY.toUnsignedBase64Url(algorithm.ecCoordinateSize),
            )

            else -> error("Unsupported test public key type: ${publicKey::class.simpleName}")
        }
        Jwk.fromValues(map)
    }

    /**
     * JWK provider backed by this in-memory public key.
     *
     * Use [OidcProviderConfig.jwt] with this [OpenIdTestKeys] instance for the common test setup. Access this provider
     * directly only for tests that need custom JWK provider behavior.
     */
    public val jwkProvider: JwkProvider = JwkProvider { requestedKeyId ->
        require(requestedKeyId == null || requestedKeyId == keyId) {
            "No key found for kid $requestedKeyId"
        }
        jwk
    }

    /**
     * Issues a signed JWT access token.
     *
     * Uses [defaultIssuer] and [defaultAudience] when set at key creation. Override per token in [configure].
     *
     * @param configure additional token claims to be included.
     * @return signed access token.
     * @throws IllegalArgumentException when issuer or audience is missing, or when a custom claim value cannot be
     * represented as a JSON claim.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdTestKeys.accessToken)
     */
    public fun accessToken(configure: OpenIdTestAccessTokenBuilder.() -> Unit = {}): String {
        val builder = OpenIdTestAccessTokenBuilder(defaultIssuer, defaultAudience, keyId).apply(configure)
        return builder.toJwtBuilder().sign()
    }

    /**
     * Issues a signed ID token.
     *
     * Uses [defaultIssuer] and [defaultAudience] when set at key creation. Override per token in [configure].
     *
     * @param subject token subject.
     * @param configure additional token claims to be included.
     * @return signed ID token.
     * @throws IllegalArgumentException when issuer or audience is missing, or when a custom claim value cannot be
     * represented as a JSON claim.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdTestKeys.idToken)
     */
    public fun idToken(subject: String, configure: OpenIdTestIdTokenBuilder.() -> Unit = {}): String {
        val builder = OpenIdTestIdTokenBuilder(defaultIssuer, defaultAudience, subject, keyId).apply(configure)
        return builder.toJwtBuilder().sign()
    }

    internal fun JWTCreator.Builder.sign(): String {
        val jwtAlgorithm = algorithm.toJwtAlgorithm(publicKey = keyPair.public, privateKey = keyPair.private)
        return sign(jwtAlgorithm)
    }

    public companion object {
        /**
         * Generates an RSA key pair for OIDC tests.
         *
         * @param keyId key ID written to token headers and exposed by the generated JWK.
         * @param algorithm RSA signature algorithm used by [OpenIdTestKeys.accessToken] and [OpenIdTestKeys.idToken].
         * @param issuer default token issuer for [OpenIdTestKeys.accessToken] and [OpenIdTestKeys.idToken].
         * @param audience default token audience for [OpenIdTestKeys.accessToken] and [OpenIdTestKeys.idToken].
         * @return generated test keys.
         * @throws IllegalArgumentException if [algorithm] is not an RSA signature algorithm.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdTestKeys.rsa)
         */
        public fun rsa(
            keyId: String = "kid-1",
            algorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA_256,
            issuer: String? = null,
            audience: String? = null,
        ): OpenIdTestKeys {
            require(algorithm.keyAlgorithm == KeyAlgorithm.RSA) {
                "RSA test keys require an RSA signature algorithm"
            }
            val keyPair = generateRsaKeyPair()
            return OpenIdTestKeys(keyId, algorithm, keyPair, issuer, audience)
        }

        /**
         * Generates an EC key pair for OIDC tests.
         *
         * @param keyId key ID written to token headers and exposed by the generated JWK.
         * @param algorithm ECDSA signature algorithm used by [OpenIdTestKeys.accessToken] and [OpenIdTestKeys.idToken].
         * @param issuer default token issuer for [OpenIdTestKeys.accessToken] and [OpenIdTestKeys.idToken].
         * @param audience default token audience for [OpenIdTestKeys.accessToken] and [OpenIdTestKeys.idToken].
         * @return generated test keys.
         * @throws IllegalArgumentException if [algorithm] is not an EC signature algorithm.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdTestKeys.ec)
         */
        public fun ec(
            keyId: String = "kid-1",
            algorithm: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA_256,
            issuer: String? = null,
            audience: String? = null,
        ): OpenIdTestKeys {
            require(algorithm.keyAlgorithm == KeyAlgorithm.EC) {
                "EC test keys require an ECDSA signature algorithm"
            }
            val keyPair = algorithm.generateEcKeyPair()
            return OpenIdTestKeys(keyId, algorithm, keyPair, issuer, audience)
        }
    }
}

/**
 * Base claim builder for test JWTs issued by [OpenIdTestKeys].
 *
 * @field issuer Token issuer. Must exactly match the provider configuration.
 * @field audience Token audience.
 *
 * Standard JWT fields are exposed as properties. Additional JSON-like claims can be added with [claim].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdTestTokenBuilder)
 */
@KtorDsl
public abstract class OpenIdTestTokenBuilder internal constructor(
    public var issuer: String?,
    public var audience: String?,
    public var subject: String?,
    public var keyId: String?,
) {

    /**
     * Issued-at timestamp. Defaults to builder creation time.
     */
    public var issuedAt: Instant? = Clock.System.now()

    /**
     * Expiration timestamp. Defaults to one hour after builder creation time.
     */
    public var expiresAt: Instant? = issuedAt?.plus(1.hours)

    /**
     * Not-before timestamp.
     */
    public var notBefore: Instant? = null

    /**
     * JWT ID.
     */
    public var jwtId: String? = null

    /**
     * Email claim.
     */
    public var email: String?
        get() = claims["email"] as? String
        set(value) {
            claims["email"] = value
        }

    /**
     * Display name claim.
     */
    public var name: String?
        get() = claims["name"] as? String
        set(value) {
            claims["name"] = value
        }

    internal val claims: MutableMap<String, Any?> = linkedMapOf()

    /**
     * Adds a custom JSON-like claim.
     *
     * Supported values are `null`, strings, booleans, numbers, [Instant], [Date], maps with string keys, arrays,
     * and iterables containing supported values.
     *
     * @param name claim name.
     * @param value claim value.
     * @throws IllegalArgumentException when [value] cannot be represented as a JSON claim.
     */
    public fun claim(name: String, value: Any?) {
        claims[name] = normalizeClaimValue(name, value)
    }

    internal fun toJwtBuilder(): JWTCreator.Builder {
        val issuer = requireNotNull(issuer?.takeIf { it.isNotBlank() }) {
            "issuer must be set on OpenIdTestKeys or in the token builder"
        }
        val audience = requireNotNull(audience?.takeIf { it.isNotBlank() }) {
            "audience must be set on OpenIdTestKeys or in the token builder"
        }
        val builder = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)

        keyId?.let { builder.withKeyId(it) }
        jwtId?.let { builder.withJWTId(it) }
        subject?.let { builder.withSubject(it) }
        issuedAt?.let { builder.withIssuedAt(it.toJavaInstant()) }
        expiresAt?.let { builder.withExpiresAt(it.toJavaInstant()) }
        notBefore?.let { builder.withNotBefore(it.toJavaInstant()) }
        claims.forEach { (name, value) -> builder.withJsonClaim(name, value) }
        return builder
    }
}

/**
 * Builder for access tokens issued by [OpenIdTestKeys].
 *
 * Access tokens do not require a subject by default, which allows service-style tokens that identify a client instead
 * of an end user.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdTestAccessTokenBuilder)
 */
@KtorDsl
public class OpenIdTestAccessTokenBuilder internal constructor(
    issuer: String?,
    audience: String?,
    keyId: String?,
) : OpenIdTestTokenBuilder(issuer, audience, subject = null, keyId = keyId) {
    /**
     * OAuth client ID claim.
     */
    public var clientId: String?
        get() = claims["client_id"] as? String
        set(value) = claim("client_id", value)
}

/**
 * Builder for ID tokens issued by [OpenIdTestKeys].
 *
 * ID tokens require a subject when created through [OpenIdTestKeys.idToken].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdTestIdTokenBuilder)
 */
@KtorDsl
public class OpenIdTestIdTokenBuilder internal constructor(
    issuer: String?,
    audience: String?,
    subject: String,
    keyId: String?,
) : OpenIdTestTokenBuilder(issuer, audience, subject, keyId) {
    /**
     * Nonce claim used by OIDC authorization-code replay protection.
     */
    public var nonce: String?
        get() = claims["nonce"] as? String
        set(value) = claim("nonce", value)

    /**
     * `at_hash` claim.
     */
    public var atHash: String?
        get() = claims["at_hash"] as? String
        set(value) = claim("at_hash", value)
}

internal fun SignatureAlgorithm.hashAccessToken(token: String): String {
    val input = token.toByteArray(Charsets.US_ASCII)
    val digest = digestAlgorithm.toDigester().digest(input)
    val src = digest.copyOfRange(0, digest.size / 2)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(src)
}

private fun generateRsaKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

private fun SignatureAlgorithm.generateEcKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec(ecParameterName))
    }.generateKeyPair()

internal fun SignatureAlgorithm.toJwtAlgorithm(publicKey: PublicKey, privateKey: PrivateKey? = null): Algorithm =
    when (val publicKey = publicKey) {
        is RSAPublicKey -> when (this) {
            SignatureAlgorithm.RSA_SHA_256 -> Algorithm.RSA256(publicKey, privateKey as RSAPrivateKey?)
            SignatureAlgorithm.RSA_SHA_384 -> Algorithm.RSA384(publicKey, privateKey as RSAPrivateKey?)
            SignatureAlgorithm.RSA_SHA_512 -> Algorithm.RSA512(publicKey, privateKey as RSAPrivateKey?)
            else -> error("Unsupported RSA JWT signing algorithm: $name")
        }

        is ECPublicKey -> when (this) {
            SignatureAlgorithm.ECDSA_SHA_256 -> Algorithm.ECDSA256(publicKey, privateKey as ECPrivateKey?)
            SignatureAlgorithm.ECDSA_SHA_384 -> Algorithm.ECDSA384(publicKey, privateKey as ECPrivateKey?)
            SignatureAlgorithm.ECDSA_SHA_512 -> Algorithm.ECDSA512(publicKey, privateKey as ECPrivateKey?)
            else -> error("Unsupported EC JWT signing algorithm: $name")
        }

        else -> error("Unsupported JWK key type: ${publicKey::class.simpleName}")
    }

private val SignatureAlgorithm.ecParameterName: String?
    get() = when (this) {
        SignatureAlgorithm.ECDSA_SHA_256 -> "secp256r1"
        SignatureAlgorithm.ECDSA_SHA_384 -> "secp384r1"
        SignatureAlgorithm.ECDSA_SHA_512 -> "secp521r1"
        else -> null
    }

internal val SignatureAlgorithm.ecJwaCurve: String?
    get() = when (this) {
        SignatureAlgorithm.ECDSA_SHA_256 -> "P-256"
        SignatureAlgorithm.ECDSA_SHA_384 -> "P-384"
        SignatureAlgorithm.ECDSA_SHA_512 -> "P-521"
        else -> null
    }

private val SignatureAlgorithm.ecCoordinateSize: Int?
    get() = when (this) {
        SignatureAlgorithm.ECDSA_SHA_256 -> 32
        SignatureAlgorithm.ECDSA_SHA_384 -> 48
        SignatureAlgorithm.ECDSA_SHA_512 -> 66
        else -> null
    }

private fun BigInteger.toUnsignedBase64Url(size: Int? = null): String {
    val value = toByteArray().stripLeadingZero()
    val bytes = if (size == null || value.size == size) {
        value
    } else {
        require(value.size <= size) {
            "EC coordinate is longer than expected"
        }
        ByteArray(size - value.size) + value
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun ByteArray.stripLeadingZero(): ByteArray =
    if (size > 1 && this[0] == 0.toByte()) copyOfRange(1, size) else this

private fun normalizeClaimValue(name: String, value: Any?): Any? =
    when (value) {
        null, is String, is Boolean, is Int, is Long, is Double, is Date, is JavaInstant -> value
        is Instant -> value.toJavaInstant()
        is Byte, is Short -> value.toInt()
        is Float -> value.toDouble()
        is Number -> value.toDouble()
        is Array<*> -> value.map { normalizeClaimValue(name, it) }
        is Iterable<*> -> value.map { normalizeClaimValue(name, it) }
        is Map<*, *> -> value.mapValues { (key, item) ->
            require(key is String) {
                "claim $name map keys must be strings"
            }
            normalizeClaimValue(name, item)
        }

        else -> throw IllegalArgumentException("Unsupported JWT claim value for $name: ${value::class.simpleName}")
    }

private fun JWTCreator.Builder.withJsonClaim(name: String, value: Any?) {
    when (value) {
        null -> withNullClaim(name)
        is String -> withClaim(name, value)
        is Boolean -> withClaim(name, value)
        is Int -> withClaim(name, value)
        is Long -> withClaim(name, value)
        is Double -> withClaim(name, value)
        is Date -> withClaim(name, value)
        is JavaInstant -> withClaim(name, value)
        is List<*> -> withClaim(name, value)
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            withClaim(name, value as Map<String, Any?>)
        }

        else -> throw IllegalArgumentException("Unsupported JWT claim value for $name: ${value::class.simpleName}")
    }
}
