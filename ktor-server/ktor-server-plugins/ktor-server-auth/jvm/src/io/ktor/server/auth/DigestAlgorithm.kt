/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import java.security.MessageDigest

/**
 * Represents a digest authentication algorithm as defined in RFC 7616.
 *
 * Each algorithm defines the hash function used for computing digests in HTTP Digest Authentication.
 * Session variants (those ending with `-sess`) modify the HA1 calculation to include the nonce and cnonce,
 * providing additional security by binding the session to specific client entropy.
 *
 * **Security Recommendation**: Use [SHA_512_256] or [SHA_512_256_SESS] for new implementations.
 * These provide the strongest security properties. MD5 variants are deprecated and should only
 * be used for backward compatibility with legacy systems.
 *
 * @property algorithmName The name of the algorithm as it appears in HTTP headers (e.g., "MD5", "SHA-256-sess")
 * @property hashName The Java Security algorithm name used for MessageDigest (e.g., "MD5", "SHA-256")
 * @property isSession Whether this is a session variant that incorporates nonce and cnonce into HA1
 */
public enum class DigestAlgorithm(
    public val algorithmName: String,
    public val hashName: String,
    public val isSession: Boolean
) {
    /** MD5 algorithm - deprecated, use only for backward compatibility with legacy systems */
    MD5("MD5", "MD5", false),

    /** MD5 session variant - deprecated, use only for backward compatibility with legacy systems */
    MD5_SESS("MD5-sess", "MD5", true),

    /** SHA-256 algorithm - minimum recommended for production use */
    SHA_256("SHA-256", "SHA-256", false),

    /** SHA-256 session variant - minimum recommended for production use */
    SHA_256_SESS("SHA-256-sess", "SHA-256", true),

    /** SHA-512/256 algorithm - **recommended for new implementations**, provides the strongest security */
    SHA_512_256("SHA-512-256", "SHA-512/256", false),

    /** SHA-512/256 session variant - **recommended for new implementations**, provides the strongest security */
    SHA_512_256_SESS("SHA-512-256-sess", "SHA-512/256", true);

    /**
     * Creates a [MessageDigest] instance for this algorithm.
     *
     * @return A new MessageDigest configured for this algorithm's hash function
     */
    public fun toDigester(): MessageDigest = MessageDigest.getInstance(hashName)

    public companion object {
        /**
         * Parses an algorithm name string into a [DigestAlgorithm].
         *
         * @param name The algorithm name (case-insensitive), e.g., "MD5", "SHA-256", "SHA-256-sess"
         * @return The corresponding [DigestAlgorithm] or null if not recognized
         */
        public fun from(name: String): DigestAlgorithm? {
            return entries.find { it.algorithmName.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Represents the quality of protection (qop) options for HTTP Digest Authentication as defined in RFC 7616.
 *
 * @property value The string value as it appears in HTTP headers
 */
public enum class DigestQop(public val value: String) {
    /**
     * Authentication only protects the integrity of the request method and URI.
     */
    AUTH("auth"),

    /**
     * Authentication with integrity protection additionally protects the integrity of the request body.
     * When using this mode, the A2 hash includes the hash of the entity body.
     */
    AUTH_INT("auth-int");

    public companion object {
        /**
         * Parses a qop string value into a [DigestQop].
         *
         * @param value The qop value (case-insensitive), e.g., "auth", "auth-int"
         * @return The corresponding [DigestQop] or null if not recognized
         */
        public fun from(value: String): DigestQop? {
            return entries.find { it.value == value }
        }
    }
}
