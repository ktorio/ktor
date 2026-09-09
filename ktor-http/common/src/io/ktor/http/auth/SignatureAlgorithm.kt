/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

/**
 * Represents a signature algorithm.
 *
 * Two signature algorithms with the same [name] are considered equivalent.
 *
 * @property name The common name of the signature algorithm
 * @property jcaAlgorithm The Java Cryptography Architecture signature algorithm name
 * @property digestAlgorithm The digest algorithm used by this signature algorithm
 * @property keyAlgorithm The key algorithm family used by this signature algorithm
 * @property xmlUri The XML Signature algorithm URI, if this algorithm has one
 * @property jwaName The JSON Web Algorithm name, if this algorithm has one
 */
public class SignatureAlgorithm(
    public val name: String,
    public val jcaAlgorithm: String,
    public val digestAlgorithm: DigestAlgorithm,
    public val keyAlgorithm: KeyAlgorithm,
    public val xmlUri: String? = null,
    public val jwaName: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignatureAlgorithm) return false

        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    public companion object {
        /** RSA with SHA-256 signature algorithm */
        public val RSA_SHA_256: SignatureAlgorithm = SignatureAlgorithm(
            name = "RSA-SHA-256",
            jcaAlgorithm = "SHA256withRSA",
            digestAlgorithm = DigestAlgorithm.SHA_256,
            keyAlgorithm = KeyAlgorithm.RSA,
            xmlUri = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
            jwaName = "RS256"
        )

        /** RSA with SHA-384 signature algorithm */
        public val RSA_SHA_384: SignatureAlgorithm = SignatureAlgorithm(
            name = "RSA-SHA-384",
            jcaAlgorithm = "SHA384withRSA",
            digestAlgorithm = DigestAlgorithm.SHA_384,
            keyAlgorithm = KeyAlgorithm.RSA,
            xmlUri = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384",
            jwaName = "RS384"
        )

        /** RSA with SHA-512 signature algorithm */
        public val RSA_SHA_512: SignatureAlgorithm = SignatureAlgorithm(
            name = "RSA-SHA-512",
            jcaAlgorithm = "SHA512withRSA",
            digestAlgorithm = DigestAlgorithm.SHA_512,
            keyAlgorithm = KeyAlgorithm.RSA,
            xmlUri = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512",
            jwaName = "RS512"
        )

        /** ECDSA with SHA-256 signature algorithm */
        public val ECDSA_SHA_256: SignatureAlgorithm = SignatureAlgorithm(
            name = "ECDSA-SHA-256",
            jcaAlgorithm = "SHA256withECDSA",
            digestAlgorithm = DigestAlgorithm.SHA_256,
            keyAlgorithm = KeyAlgorithm.EC,
            xmlUri = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256",
            jwaName = "ES256"
        )

        /** ECDSA with SHA-384 signature algorithm */
        public val ECDSA_SHA_384: SignatureAlgorithm = SignatureAlgorithm(
            name = "ECDSA-SHA-384",
            jcaAlgorithm = "SHA384withECDSA",
            digestAlgorithm = DigestAlgorithm.SHA_384,
            keyAlgorithm = KeyAlgorithm.EC,
            xmlUri = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384",
            jwaName = "ES384"
        )

        /** ECDSA with SHA-512 signature algorithm */
        public val ECDSA_SHA_512: SignatureAlgorithm = SignatureAlgorithm(
            name = "ECDSA-SHA-512",
            jcaAlgorithm = "SHA512withECDSA",
            digestAlgorithm = DigestAlgorithm.SHA_512,
            keyAlgorithm = KeyAlgorithm.EC,
            xmlUri = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512",
            jwaName = "ES512"
        )

        /**
         * Parses an XML Signature algorithm URI into a [SignatureAlgorithm].
         *
         * @param uri The XML Signature algorithm URI
         * @return The corresponding [SignatureAlgorithm] or null if not recognized
         */
        public fun fromXmlUri(uri: String): SignatureAlgorithm? {
            return DEFAULT_ALGORITHMS.find { it.xmlUri == uri }
        }

        /**
         * Parses a JSON Web Algorithm name into a [SignatureAlgorithm].
         *
         * @param name The JSON Web Algorithm name
         * @return The corresponding [SignatureAlgorithm] or null if not recognized
         */
        public fun fromJwaName(name: String): SignatureAlgorithm? {
            return DEFAULT_ALGORITHMS.find { it.jwaName == name }
        }

        private val DEFAULT_ALGORITHMS: List<SignatureAlgorithm> = listOf(
            RSA_SHA_256,
            RSA_SHA_384,
            RSA_SHA_512,
            ECDSA_SHA_256,
            ECDSA_SHA_384,
            ECDSA_SHA_512,
        )
    }
}
