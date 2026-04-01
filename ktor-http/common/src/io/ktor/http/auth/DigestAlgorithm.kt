/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

/**
 * Represents a hash algorithm as defined in RFC 7616.
 *
 * Each algorithm defines the hash function used for computing digests in HTTP Digest Authentication.
 * Session variants (those ending with `-sess`) modify the HA1 calculation to include the nonce and cnonce,
 * providing additional security by binding the session to specific client entropy.
 *
 * **Security Recommendation**: Use [SHA_512_256] or [SHA_512_256_SESS] for new implementations.
 * These provide the strongest security properties. MD5 variants are deprecated and should only
 * be used for backward compatibility with legacy systems.
 *
 * @property name The name of the algorithm as it appears in HTTP headers (e.g., "MD5", "SHA-256-sess")
 * @property hashName The Java Security algorithm name used for MessageDigest (e.g., "MD5", "SHA-256")
 * @property isSession Whether this is a session variant that incorporates nonce and cnonce into HA1
 */
public class DigestAlgorithm(
    public val name: String,
    public val hashName: String,
    public val isSession: Boolean
) {
    public companion object {
        /** MD5 algorithm - deprecated, use only for backward compatibility with legacy systems */
        @Deprecated("MD5 is deprecated because it is not secure")
        public val MD5: DigestAlgorithm = DigestAlgorithm("MD5", "MD5", isSession = false)

        /** MD5 session variant - deprecated, use only for backward compatibility with legacy systems */
        @Deprecated("MD5-sess is deprecated because it is not secure")
        public val MD5_SESS: DigestAlgorithm = DigestAlgorithm("MD5-sess", "MD5", isSession = true)

        /** SHA-256 algorithm - minimum recommended for production use */
        public val SHA_256: DigestAlgorithm = DigestAlgorithm("SHA-256", "SHA-256", isSession = false)

        /** SHA-256 session variant - minimum recommended for production use */
        public val SHA_256_SESS: DigestAlgorithm = DigestAlgorithm("SHA-256-sess", "SHA-256", isSession = true)

        /** SHA-512/256 algorithm - **recommended for new implementations**, provides the strongest security */
        public val SHA_512_256: DigestAlgorithm = DigestAlgorithm("SHA-512-256", "SHA-512/256", isSession = false)

        /** SHA-512/256 session variant - **recommended for new implementations**, provides the strongest security */
        public val SHA_512_256_SESS: DigestAlgorithm =
            DigestAlgorithm("SHA-512-256-sess", "SHA-512/256", isSession = true)

        @Suppress("Deprecation")
        public val DEFAULT_ALGORITHMS: List<DigestAlgorithm> =
            listOf(SHA_512_256, SHA_512_256_SESS, SHA_256, SHA_256_SESS, MD5, MD5_SESS)

        /**
         * Parses an algorithm name string into a [DigestAlgorithm].
         *
         * @param name The algorithm name (case-insensitive), e.g., "MD5", "SHA-256", "SHA-256-sess"
         * @return The corresponding [DigestAlgorithm] or null if not recognized
         */
        public fun from(name: String): DigestAlgorithm? {
            return DEFAULT_ALGORITHMS.find { it.name.equals(other = name, ignoreCase = true) }
        }
    }
}

/**
 * Represents the quality of protection (qop) options for HTTP Digest Authentication as defined in RFC 7616.
 *
 * @property value The string value as it appears in HTTP headers
 */
public class DigestQop(public val value: String) {
    public companion object {
        /**
         * Authentication only protects the integrity of the request method and URI.
         */
        public val AUTH: DigestQop = DigestQop("auth")

        /**
         * Authentication with integrity protection additionally protects the integrity of the request body.
         * When using this mode, the A2 hash includes the hash of the entity body.
         */
        public val AUTH_INT: DigestQop = DigestQop("auth-int")

        public val DEFAULT_QOPS: List<DigestQop> = listOf(AUTH, AUTH_INT)

        /**
         * Parses a qop string value into a [DigestQop].
         *
         * @param value The qop value (case-insensitive), e.g., "auth", "auth-int"
         * @return The corresponding [DigestQop] or null if not recognized
         */
        public fun from(value: String): DigestQop? {
            return DEFAULT_QOPS.find { it.value.equals(value, ignoreCase = true) }
        }
    }
}
