/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.saml.saml2.core.NameIDType

/**
 * Represents a SAML NameID format.
 *
 * NameID formats specify how the user identifier (NameID) should be formatted in SAML assertions.
 * This class provides type-safe constants for common formats and allows custom formats.
 *
 * @property uri The URI string representing the NameID format
 */
@JvmInline
public value class NameIdFormat(
    public val uri: String
) {
    public companion object {
        /**
         * The NameID will be formatted as an email address (e.g., "user@example.com").
         * This is commonly used when the user's email is the primary identifier.
         */
        public val Email: NameIdFormat = NameIdFormat(uri = NameIDType.EMAIL)

        /**
         * A persistent, opaque identifier that remains constant for a given user across
         * multiple sessions and potentially across different Service Providers.
         * Useful for account linking without exposing personal information.
         */
        public val Persistent: NameIdFormat = NameIdFormat(uri = NameIDType.PERSISTENT)

        /**
         * A temporary identifier that is unique to each authentication session.
         * The identifier changes with each authentication and should not be used
         * for account linking. Provides enhanced privacy.
         */
        public val Transient: NameIdFormat = NameIdFormat(uri = NameIDType.TRANSIENT)

        /**
         * Allows the IdP to choose an appropriate format.
         */
        public val Unspecified: NameIdFormat = NameIdFormat(uri = NameIDType.UNSPECIFIED)
    }
}

/**
 * Represents a signature algorithm for SAML XML signatures.
 * Two signature algorithms with the same URI are considered equivalent.
 * This class provides type-safe constants for common signature algorithms used in SAML.
 *
 * @property uri The URI string representing the signature algorithm (XML Signature standard)
 * @property jcaAlgorithm The Java Cryptography Architecture (JCA) algorithm name
 */
public class SignatureAlgorithm(
    public val uri: String,
    public val jcaAlgorithm: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignatureAlgorithm) return false
        return uri == other.uri
    }

    override fun hashCode(): Int = uri.hashCode()

    public companion object {
        /**
         * RSA with SHA-256 signature algorithm.
         * This is the recommended default for most deployments.
         */
        public val RSA_SHA256: SignatureAlgorithm = SignatureAlgorithm(
            uri = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
            jcaAlgorithm = "SHA256withRSA"
        )

        /**
         * RSA with SHA-384 signature algorithm.
         */
        public val RSA_SHA384: SignatureAlgorithm = SignatureAlgorithm(
            uri = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384",
            jcaAlgorithm = "SHA384withRSA"
        )

        /**
         * RSA with SHA-512 signature algorithm.
         * Provides the strongest security for RSA-based signatures.
         */
        public val RSA_SHA512: SignatureAlgorithm = SignatureAlgorithm(
            uri = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512",
            jcaAlgorithm = "SHA512withRSA"
        )

        /**
         * ECDSA with SHA-256 signature algorithm.
         * Provides strong security with smaller key sizes compared to RSA.
         */
        public val ECDSA_SHA256: SignatureAlgorithm = SignatureAlgorithm(
            uri = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256",
            jcaAlgorithm = "SHA256withECDSA"
        )

        /**
         * ECDSA with SHA-384 signature algorithm.
         */
        public val ECDSA_SHA384: SignatureAlgorithm = SignatureAlgorithm(
            uri = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384",
            jcaAlgorithm = "SHA384withECDSA"
        )

        /**
         * ECDSA with SHA-512 signature algorithm.
         * Provides the strongest security for ECDSA-based signatures.
         */
        public val ECDSA_SHA512: SignatureAlgorithm = SignatureAlgorithm(
            uri = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512",
            jcaAlgorithm = "SHA512withECDSA"
        )

        /**
         * Returns the predefined [SignatureAlgorithm] matching the given [uri], or `null` if the URI
         * does not correspond to any known algorithm.
         *
         * `@param` uri The XML Signature algorithm URI
         * `@return` The matching [SignatureAlgorithm], or `null`
         */
        public fun from(uri: String): SignatureAlgorithm? = when (uri) {
            RSA_SHA256.uri -> RSA_SHA256
            RSA_SHA384.uri -> RSA_SHA384
            RSA_SHA512.uri -> RSA_SHA512
            ECDSA_SHA256.uri -> ECDSA_SHA256
            ECDSA_SHA384.uri -> ECDSA_SHA384
            ECDSA_SHA512.uri -> ECDSA_SHA512
            else -> null
        }
    }
}

/**
 * Represents a digest algorithm for SAML XML signatures.
 *
 * This class provides type-safe constants for common digest algorithms used in SAML signature references.
 *
 * @property uri The URI string representing the digest algorithm (XML Signature standard)
 */
@JvmInline
public value class DigestAlgorithm(
    public val uri: String
) {
    public companion object {
        /**
         * SHA-256 digest algorithm.
         * This is the recommended default for most deployments.
         */
        public val SHA256: DigestAlgorithm = DigestAlgorithm(
            uri = "http://www.w3.org/2001/04/xmlenc#sha256"
        )

        /**
         * SHA-384 digest algorithm.
         */
        public val SHA384: DigestAlgorithm = DigestAlgorithm(
            uri = "http://www.w3.org/2001/04/xmldsig-more#sha384"
        )

        /**
         * SHA-512 digest algorithm.
         * Provides the strongest security.
         */
        public val SHA512: DigestAlgorithm = DigestAlgorithm(
            uri = "http://www.w3.org/2001/04/xmlenc#sha512"
        )
    }
}

/**
 * Specifies how the SAML AuthnRequest should be sent to the IdP.
 *
 * ## Choosing a Binding
 *
 * - **HTTP-Redirect** (default): Simpler, works for small requests. Limited by URL length.
 *   The signature is in the query string.
 * - **HTTP-POST**: Better for larger requests. Signature is embedded in XML.
 *   Requires JavaScript or user interaction to submit the form.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.SamlBinding)
 */
public enum class SamlBinding {
    /**
     * HTTP-Redirect binding.
     *
     * The AuthnRequest is deflated, Base64-encoded, URL-encoded, and sent as a query parameter.
     * This is the most commonly used binding for AuthnRequests.
     */
    HttpRedirect,

    /**
     * HTTP-POST binding.
     *
     * The AuthnRequest is Base64-encoded (not deflated) and sent in an HTML form.
     * The browser automatically submits the form using JavaScript.
     */
    HttpPost
}

/**
 * Pre-defined sets of secure cryptographic algorithms for SAML signature validation.
 *
 * ## Example Usage
 *
 * ```kotlin
 * saml("saml-auth") {
 *     // Use recommended secure algorithms
 *     allowedSignatureAlgorithms = SamlAlgorithms.RECOMMENDED_SIGNATURE_ALGORITHMS
 *     allowedDigestAlgorithms = SamlAlgorithms.RECOMMENDED_DIGEST_ALGORITHMS
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.SamlAlgorithms)
 */
public object SamlAlgorithms {
    /**
     * Recommended signature algorithms (SHA-256 and above).
     *
     * Includes RSA and ECDSA variants with SHA-256, SHA-384, and SHA-512.
     * Excludes SHA-1 based algorithms which are vulnerable to collision attacks.
     */
    public val RECOMMENDED_SIGNATURE_ALGORITHMS: Set<SignatureAlgorithm> = setOf(
        SignatureAlgorithm.RSA_SHA256,
        SignatureAlgorithm.RSA_SHA384,
        SignatureAlgorithm.RSA_SHA512,
        SignatureAlgorithm.ECDSA_SHA256,
        SignatureAlgorithm.ECDSA_SHA384,
        SignatureAlgorithm.ECDSA_SHA512
    )

    /**
     * Recommended digest algorithms (SHA-256 and above).
     *
     * Includes SHA-256, SHA-384, and SHA-512.
     * Excludes SHA-1 and MD5, which are vulnerable to collision attacks.
     */
    public val RECOMMENDED_DIGEST_ALGORITHMS: Set<DigestAlgorithm> = setOf(
        DigestAlgorithm.SHA256,
        DigestAlgorithm.SHA384,
        DigestAlgorithm.SHA512
    )
}

/**
 * Represents a SAML AuthnContext class reference.
 *
 * AuthnContext specifies the authentication method used or required by the IdP.
 * This class provides type-safe constants for common authentication contexts.
 *
 * ## Example Usage
 *
 * ```kotlin
 * saml("saml-auth") {
 *     // Require multi-factor authentication
 *     requestedAuthnContext = SamlAuthnContext.MultiFactor
 *
 *     // Or use a custom context
 *     requestedAuthnContext = SamlAuthnContext("urn:custom:authn:context")
 * }
 * ```
 *
 * @property uri The URI string representing the AuthnContext class reference
 */
@JvmInline
public value class SamlAuthnContext(
    public val uri: String
) {
    public companion object {
        /**
         * Password authentication.
         *
         * The user is authenticated with a username and password.
         */
        public val Password: SamlAuthnContext =
            SamlAuthnContext("urn:oasis:names:tc:SAML:2.0:ac:classes:Password")

        /**
         * Password with protected transport authentication.
         *
         * The user is authenticated with a username and password over a secure channel (e.g., HTTPS).
         * This is more secure than plain password authentication.
         */
        public val PasswordProtectedTransport: SamlAuthnContext =
            SamlAuthnContext("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport")

        /**
         * Multifactor authentication.
         *
         * The user is authenticated using multiple factors (e.g., password + OTP, password and biometric).
         * This provides stronger security than single-factor authentication.
         */
        public val MultiFactor: SamlAuthnContext =
            SamlAuthnContext("urn:oasis:names:tc:SAML:2.0:ac:classes:MultiFactor")

        /**
         * Kerberos authentication.
         *
         * The user is authenticated using Kerberos protocol.
         */
        public val Kerberos: SamlAuthnContext =
            SamlAuthnContext("urn:oasis:names:tc:SAML:2.0:ac:classes:Kerberos")

        /**
         * X.509 certificate authentication.
         *
         * The user authenticated using an X.509 client certificate.
         */
        public val X509: SamlAuthnContext =
            SamlAuthnContext("urn:oasis:names:tc:SAML:2.0:ac:classes:X509")
    }
}
