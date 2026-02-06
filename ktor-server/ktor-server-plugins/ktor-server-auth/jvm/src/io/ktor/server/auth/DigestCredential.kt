/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import java.security.MessageDigest

/**
 * Digest credentials.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestCredential)
 *
 * @see [digest]
 *
 * @property realm A digest authentication realm identifying the protection space
 * @property userName The username, or userhash if [userHash] is true
 * @property digestUri The request URI (an absolute URI or `*`)
 * @property nonce A server-specified unique value for this authentication round
 * @property opaque A string of data that should be returned by the client unchanged
 * @property nonceCount The request counter (nc); must be sent if [qop] is specified
 * @property algorithm The digest algorithm name (e.g., "SHA-512-256", "SHA-256", "MD5")
 * @property response The client's digest response, consisting of hex digits
 * @property cnonce Client nonce, must be sent if [qop] is specified
 * @property qop Quality of protection ("auth" or "auth-int")
 * @property userHash Whether the [userName] field contains a hashed username instead of plaintext
 * @property charset The character encoding used (typically "UTF-8" or "ISO-8859-1")
 */
public class DigestCredential(
    public val realm: String,
    public val userName: String,
    public val digestUri: String,
    public val nonce: String,
    public val opaque: String?,
    public val nonceCount: String?,
    public val algorithm: String?,
    public val response: String,
    public val cnonce: String?,
    public val qop: String?,
    public val userHash: Boolean = false,
    public val charset: Charset = Charsets.ISO_8859_1
) {
    internal val digestAlgorithm = algorithm?.let { DigestAlgorithm.from(it) } ?: DigestAlgorithm.MD5

    internal val digester = digestAlgorithm.toDigester()

    @Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(
        realm: String,
        userName: String,
        digestUri: String,
        nonce: String,
        opaque: String?,
        nonceCount: String?,
        algorithm: String?,
        response: String,
        cnonce: String?,
        qop: String?,
    ) : this(realm, userName, digestUri, nonce, opaque, nonceCount, algorithm, response, cnonce, qop)


    /**
     * Creates a copy of this DigestCredential, optionally replacing some properties.
     */
    public fun copy(
        realm: String = this.realm,
        userName: String = this.userName,
        digestUri: String = this.digestUri,
        nonce: String = this.nonce,
        opaque: String? = this.opaque,
        nonceCount: String? = this.nonceCount,
        algorithm: String? = this.algorithm,
        response: String = this.response,
        cnonce: String? = this.cnonce,
        qop: String? = this.qop
    ): DigestCredential = DigestCredential(
        realm, userName, digestUri, nonce, opaque, nonceCount, algorithm,
        response, cnonce, qop, userHash, charset
    )

    /**
     * Returns the values of all properties in the order they were declared.
     */
    public operator fun component1(): String = realm
    public operator fun component2(): String = userName
    public operator fun component3(): String = digestUri
    public operator fun component4(): String = nonce
    public operator fun component5(): String? = opaque
    public operator fun component6(): String? = nonceCount
    public operator fun component7(): String? = algorithm
    public operator fun component8(): String = response
    public operator fun component9(): String? = cnonce
    public operator fun component10(): String? = qop

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DigestCredential) return false

        if (realm != other.realm) return false
        if (userName != other.userName) return false
        if (digestUri != other.digestUri) return false
        if (nonce != other.nonce) return false
        if (opaque != other.opaque) return false
        if (nonceCount != other.nonceCount) return false
        if (algorithm != other.algorithm) return false
        if (response != other.response) return false
        if (cnonce != other.cnonce) return false
        if (qop != other.qop) return false
        if (userHash != other.userHash) return false
        if (charset != other.charset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = realm.hashCode()
        result = 31 * result + userName.hashCode()
        result = 31 * result + digestUri.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + (opaque?.hashCode() ?: 0)
        result = 31 * result + (nonceCount?.hashCode() ?: 0)
        result = 31 * result + (algorithm?.hashCode() ?: 0)
        result = 31 * result + response.hashCode()
        result = 31 * result + (cnonce?.hashCode() ?: 0)
        result = 31 * result + (qop?.hashCode() ?: 0)
        result = 31 * result + userHash.hashCode()
        result = 31 * result + charset.hashCode()
        return result
    }

    override fun toString(): String {
        return "DigestCredential(" +
            "realm='$realm', " +
            "userName='$userName', " +
            "digestUri='$digestUri', " +
            "nonce='$nonce', " +
            "opaque=$opaque, " +
            "nonceCount=$nonceCount, " +
            "algorithm=$algorithm, " +
            "response='$response', " +
            "cnonce=$cnonce, " +
            "qop=$qop, " +
            "userHash=$userHash, " +
            "charset=$charset" +
            ")"
    }
}

/**
 * Converts [HttpAuthHeader] to [DigestCredential].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.toDigestCredential)
 */
public fun HttpAuthHeader.Parameterized.toDigestCredential(
    defaultCharset: Charset = Charsets.UTF_8
): DigestCredential {
    val userHash = parameter("userhash")?.toBoolean() ?: false
    val username = parameter("username")
    val usernameStar = parameter("username*")
    val decodedUserName = when {
        usernameStar != null -> decodeHeaderParameterWithEncoding(usernameStar)
        username != null -> username
        else -> error("Missing username parameter in digest authentication header")
    }
    val charset = parameter("charset")?.let { Charsets.forName(it) } ?: defaultCharset

    return DigestCredential(
        parameter("realm")!!,
        decodedUserName,
        parameter("uri")!!,
        parameter("nonce")!!,
        parameter("opaque"),
        parameter("nc"),
        parameter("algorithm"),
        parameter("response")!!,
        parameter("cnonce"),
        parameter("qop"),
        userHash,
        charset
    )
}

/**
 * Decode header parameter according to RFC 8187
 */
internal fun decodeHeaderParameterWithEncoding(value: String): String {
    val firstQuoteIndex = value.indexOf('\'')
    val lastQuoteIndex = value.lastIndexOf('\'')
    if (firstQuoteIndex == -1 || lastQuoteIndex == -1 || firstQuoteIndex == lastQuoteIndex) {
        return value
    }
    val charsetName = value.substring(0, firstQuoteIndex)
    val encodedValue = value.substring(lastQuoteIndex + 1)
    val charset = runCatching { Charsets.forName(charsetName) }
        .getOrDefault(Charsets.UTF_8)
    return encodedValue.decodeURLPart(charset = charset)
}

/**
 * Verifies that credentials are valid for a given [method], and [userNameRealmPasswordDigest].
 * The algorithm from [DigestCredential.algorithm] is used to compute the HA1 value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.verifier)
 */
public suspend fun DigestCredential.verifier(
    method: HttpMethod,
    userNameRealmPasswordDigest: suspend (String, String) -> ByteArray?
): Boolean {
    val userNameRealmPasswordDigestResult = userNameRealmPasswordDigest(userName, realm)
    // here we do null-check in the end because it should always be time-constant comparison due to security reasons
    val ha1 = userNameRealmPasswordDigestResult ?: ByteArray(0)
    return verifyWithHA1(method, ha1) && userNameRealmPasswordDigestResult != null
}

/**
 * Verifies that credentials are valid using a pre-computed HA1 value.
 */
internal fun DigestCredential.verifyWithHA1(
    method: HttpMethod,
    ha1: ByteArray,
    entityBodyHash: ByteArray? = null
): Boolean {
    val ha1Hex = hex(bytes = ha1)
    val validDigest = computeDigestResponse(method, ha1Hex, entityBodyHash)
    val incoming: ByteArray = try {
        hex(response)
    } catch (_: NumberFormatException) {
        return false
    }
    return MessageDigest.isEqual(incoming, validDigest)
}

/**
 * Calculates the expected digest bytes for this [DigestCredential] per RFC 7616.
 *
 * This function implements the full RFC 7616 digest calculation including
 * - Session algorithm support (where HA1 includes nonce and cnonce)
 * - auth-int support (where HA2 includes the entity body hash)
 *
 * @param method The HTTP method of the request
 * @param userNameRealmPasswordDigest The H(username:realm:password) value
 * @param entityBodyHash The hash of the request entity body (for qop=auth-int)
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.expectedDigest)
 */
public fun DigestCredential.expectedDigest(
    method: HttpMethod,
    userNameRealmPasswordDigest: ByteArray,
    entityBodyHash: ByteArray? = null
): ByteArray {
    val ha1Hex = hex(bytes = computeHA1(userNameRealmPasswordDigest))
    return computeDigestResponse(method, ha1Hex, entityBodyHash)
}

internal fun DigestCredential.digest(data: String): ByteArray {
    digester.reset()
    return digester.digest(data.toByteArray(charset))
}

private fun DigestCredential.computeDigestResponse(
    method: HttpMethod,
    ha1Hex: String,
    entityBodyHash: ByteArray?
): ByteArray {
    // H(A2) calculation per RFC 7616 Section 3.4.3
    // For qop=auth (or no qop): H(A2) = H(method:uri)
    // For qop=auth-int: H(A2) = H(method:uri:H(entity-body))
    val methodValue = method.value.toUpperCasePreservingASCIIRules()
    val a2 = if (qop == DigestQop.AUTH_INT.value && entityBodyHash != null) {
        "$methodValue:$digestUri:${hex(entityBodyHash)}"
    } else {
        "$methodValue:$digestUri"
    }
    val ha2 = hex(digest(a2))

    // Final response calculation per RFC 7616 Section 3.4.1
    // Without qop: response = H(H(A1):nonce:H(A2))
    // With qop: response = H(H(A1):nonce:nc:cnonce:qop:H(A2))
    val hashParameters = when (qop) {
        null -> listOf(ha1Hex, nonce, ha2)
        else -> listOf(ha1Hex, nonce, nonceCount, cnonce, qop, ha2)
    }.joinToString(":")

    return digest(hashParameters)
}

/**
 * Computes the HA1 value for this credential according to RFC 7616.
 *
 * For standard algorithms: HA1 = H(user:realm:pass)
 * For session algorithms: HA1 = H(H(user:realm:pass):nonce:cnonce)
 *
 * @param userNameRealmPasswordDigest The H(username:realm:password) value from the digest provider
 * @return The computed HA1 as a byte array
 */
internal fun DigestCredential.computeHA1(userNameRealmPasswordDigest: ByteArray): ByteArray {
    if (!digestAlgorithm.isSession || cnonce == null) {
        return userNameRealmPasswordDigest
    }
    val baseHa1 = hex(bytes = userNameRealmPasswordDigest)
    return digest("$baseHa1:$nonce:$cnonce")
}

/**
 * Builds the Authentication-Info header value for successful digest authentication per RFC 7616.
 *
 * This header provides mutual authentication by including `rspauth`, which allows the client
 * to verify the server knows the shared secret.
 *
 * The rspauth value is computed as:
 * - rspauth = H(HA1:nonce:nc:cnonce:qop:H(A2))
 * - where A2 = ":uri" for auth, or ":uri:H(response-body)" for auth-int
 *
 * Note: For auth-int, this implementation uses an empty response body hash since
 * the response body is not available at header generation time. This is a known
 * limitation that affects integrity verification of the response body.
 *
 * @param ha1 The computed HA1 value (already adjusted for session algorithms if applicable)
 * @param nextNonce The next nonce value for the client to use
 * @param responseBodyHash Optional hash of the response body for auth-int (defaults to empty body hash)
 * @return The formatted Authentication-Info header value
 */
internal fun DigestCredential.buildAuthenticationInfoHeader(
    ha1: ByteArray,
    nextNonce: String,
    responseBodyHash: ByteArray? = null
): String {
    // Compute rspauth: H(HA1:nonce:nc:cnonce:qop:H(A2))
    // A2 for rspauth uses empty method per RFC 7616 Section 3.4.3:
    // If the qop value is "auth" or is unspecified, then A2 is: A2 = ":" request-uri
    // If the qop value is "auth-int", then A2 is: A2 = ":" request-uri ":" H(entity-body)
    val a2 = when (qop) {
        DigestQop.AUTH_INT.value -> {
            val bodyHash = responseBodyHash ?: digest("")
            ":$digestUri:${hex(bodyHash)}"
        }

        else -> ":$digestUri"
    }
    val ha2 = hex(digest(a2))
    val ha1Hex = hex(ha1)

    // Calculate rspauth with qop (always present when this function is called)
    val rspAuthInput = "$ha1Hex:$nonce:$nonceCount:$cnonce:$qop:$ha2"
    val rspAuth = hex(digest(rspAuthInput))

    return buildString {
        append("rspauth=\"$rspAuth\"")
        append(", qop=$qop")
        append(", nc=$nonceCount")
        append(", cnonce=\"$cnonce\"")
        append(", nextnonce=\"$nextNonce\"")
    }
}
