/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwk.InvalidPublicKeyException
import com.auth0.jwk.Jwk
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.SignatureGenerationException
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.auth.*
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64

/**
 * JWT [Algorithm] backed directly by JCA, for algorithms that `java-jwt` does not implement (RSASSA-PSS, EdDSA).
 * Both produce raw JWS signatures, so no signature format conversion is needed.
 *
 * Only token and key problems are reported as [SignatureVerificationException] / [SignatureGenerationException].
 * JVM configuration problems (missing algorithm or unsupported parameters) propagate unchanged.
 */
internal class JcaJwtAlgorithm(
    private val signatureAlgorithm: SignatureAlgorithm,
    private val publicKey: PublicKey,
    private val privateKey: PrivateKey?,
) : Algorithm(checkNotNull(signatureAlgorithm.jwaName), signatureAlgorithm.jcaAlgorithm) {

    override fun verify(jwt: DecodedJWT) {
        val signature = signatureAlgorithm.toJcaSignature()
        val verified = try {
            val signatureBytes = JwsBase64.decode(jwt.signature)
            signature.initVerify(publicKey)
            signature.update("${jwt.header}.${jwt.payload}".toByteArray(Charsets.US_ASCII))
            signature.verify(signatureBytes)
        } catch (cause: IllegalArgumentException) {
            throw SignatureVerificationException(this, cause)
        } catch (cause: InvalidKeyException) {
            throw SignatureVerificationException(this, cause)
        } catch (cause: SignatureException) {
            throw SignatureVerificationException(this, cause)
        }
        if (!verified) {
            throw SignatureVerificationException(this)
        }
    }

    override fun sign(headerBytes: ByteArray, payloadBytes: ByteArray): ByteArray =
        sign { signature ->
            signature.update(headerBytes)
            signature.update('.'.code.toByte())
            signature.update(payloadBytes)
        }

    override fun sign(contentBytes: ByteArray): ByteArray =
        sign { signature -> signature.update(contentBytes) }

    private inline fun sign(update: (Signature) -> Unit): ByteArray {
        val signature = signatureAlgorithm.toJcaSignature()
        return try {
            signature.initSign(
                checkNotNull(privateKey) {
                    "Private key is required to sign ${signatureAlgorithm.name}"
                }
            )
            update(signature)
            signature.sign()
        } catch (cause: InvalidKeyException) {
            throw SignatureGenerationException(this, cause)
        } catch (cause: SignatureException) {
            throw SignatureGenerationException(this, cause)
        }
    }
}

// JWS signatures are unpadded base64url (RFC 7515)
private val JwsBase64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

// Tolerate padded JWK values
private val JwkBase64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

internal const val ED25519_CURVE = "Ed25519"
private const val ED25519_KEY_SIZE = 32

// DER SubjectPublicKeyInfo header for an Ed25519 key (OID 1.3.101.112), followed by the 32-byte raw key.
private val ED25519_SPKI_PREFIX = byteArrayOf(
    0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
)

/**
 * Resolves the JWK public key, decoding `OKP` keys that `jwks-rsa` does not support.
 *
 * @throws InvalidPublicKeyException when the key cannot be decoded.
 */
internal fun Jwk.toPublicKey(): PublicKey {
    if (type != "OKP") return publicKey

    val curve = additionalAttributes["crv"]
    if (curve != ED25519_CURVE) {
        throw InvalidPublicKeyException("Unsupported OKP JWK curve: $curve")
    }
    val encodedKey = additionalAttributes["x"] as? String
        ?: throw InvalidPublicKeyException("OKP JWK is missing the 'x' parameter")
    val rawKey = try {
        JwkBase64.decode(encodedKey)
    } catch (cause: IllegalArgumentException) {
        throw InvalidPublicKeyException("OKP JWK 'x' parameter is not valid base64url", cause)
    }
    if (rawKey.size != ED25519_KEY_SIZE) {
        throw InvalidPublicKeyException("Ed25519 JWK 'x' must be $ED25519_KEY_SIZE bytes, got ${rawKey.size}")
    }
    return try {
        KeyFactory.getInstance(ED25519_CURVE).generatePublic(X509EncodedKeySpec(ED25519_SPKI_PREFIX + rawKey))
    } catch (cause: GeneralSecurityException) {
        throw InvalidPublicKeyException("Failed to decode Ed25519 JWK", cause)
    }
}

private val jvmAvailability = ConcurrentHashMap<SignatureAlgorithm, Boolean>()

/**
 * Checks whether the installed JCA providers support this algorithm.
 */
internal fun SignatureAlgorithm.isAvailableInJvm(): Boolean = jvmAvailability.getOrPut(this) {
    try {
        toJcaSignature()
        if (keyAlgorithm == KeyAlgorithm.OKP) KeyFactory.getInstance(jcaAlgorithm)
        true
    } catch (_: GeneralSecurityException) {
        false
    }
}

internal fun SignatureAlgorithm.unavailableMessage(): String {
    val requirement = when {
        keyAlgorithm == KeyAlgorithm.OKP -> "JDK 15+ or a JCA provider supporting $jcaAlgorithm"
        jcaAlgorithm == "RSASSA-PSS" -> "JDK 11+ or a JCA provider supporting RSASSA-PSS"
        else -> "a JCA provider supporting $jcaAlgorithm"
    }
    return "JWT algorithm ${jwaName ?: name} is not available in this JVM; it requires $requirement"
}
