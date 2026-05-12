/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.utils.io.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Creates a new SAML configuration.
 *
 * This factory function allows creating a standalone [SamlConfig] instance that can be
 * reused across different SAML operations (authentication, logout).
 *
 * ## Example Usage
 *
 * ```kotlin
 * val sp = SamlSpMetadata {
 *     spEntityId = "https://myapp.example.com/saml/metadata"
 *     acsUrl = "https://myapp.example.com/saml/acs"
 *     signingCredential = SamlCrypto.loadCredential(
 *         keystorePath = "/path/to/keystore.jks",
 *         keystorePassword = "password",
 *         keyAlias = "sp-key",
 *         keyPassword = "password"
 *     )
 * }
 *
 * // Use in authentication
 * install(Authentication) {
 *     saml("saml-auth") {
 *         this.sp = sp
 *         idp = parseSamlIdpMetadata(idpMetadataXml)
 *         validate { credential -> SamlPrincipal(credential.assertion) }
 *     }
 * }
 *
 * // Use for metadata generation
 * val metadataXml = sp.toXml()
 *
 * // Use for logout
 * call.samlLogout(principal, sp, idpSloUrl)
 * ```
 *
 * @param configure Configuration block for SAML settings
 * @return A configured [SamlConfig] instance
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.SamlConfig)
 */
public fun SamlConfig(configure: SamlConfig.() -> Unit): SamlConfig =
    SamlConfig(name = null, description = null).apply(configure)

/**
 * Configuration for SAML authentication.
 *
 * Use the [SamlConfig] factory function to create instances.
 *
 * @param name The name of the authentication provider
 * @param description Optional description of the provider
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.SamlConfig)
 */
@KtorDsl
public class SamlConfig internal constructor(
    name: String?,
    description: String?
) : AuthenticationProvider.Config(name, description) {

    /**
     * The Service Provider metadata configuration.
     *
     * This contains the SP's entity ID, ACS URL, SLO URL, certificates, and other
     * metadata that can be shared across different SAML configurations.
     *
     * ## Example
     * ```kotlin
     * val spMetadata = SamlSpMetadata {
     *     spEntityId = "https://myapp.example.com/saml/metadata"
     *     acsUrl = "https://myapp.example.com/saml/acs"
     *     signingCredential = SamlCrypto.loadCredential(...)
     * }
     *
     * saml("saml-auth") {
     *     sp = spMetadata
     *     idp = parseSamlIdpMetadata(xmlString)
     * }
     * ```
     */
    public var sp: SamlSpMetadata? = null

    /**
     * The parsed Identity Provider (IdP) metadata.
     *
     * This should be obtained using [parseSamlIdpMetadata].
     *
     * ## Example
     * ```kotlin
     * saml("saml-auth") {
     *     idp = parseSamlIdpMetadata(xmlString)
     * }
     * ```
     */
    public var idp: IdPMetadata? = null

    /**
     * Whether to enable Single Logout (SLO) support.
     *
     * When `true`:
     * - The SP will handle LogoutRequests and LogoutResponses at the SLO URL
     * - You can use `ApplicationCall.samlLogout()` to initiate logout to the IdP
     * - The SP will expose an SLO endpoint that the IdP can call
     *
     * When `false` (default):
     * - SLO endpoints are not handled
     * - Only local session cleanup is performed on logout
     *
     * **Note:** SLO requires that the IdP metadata includes a SingleLogoutService endpoint.
     * Check [IdPMetadata.sloUrl] to verify SLO is available.
     */
    public var enableSingleLogout: Boolean = false

    /**
     * The HTTP binding to use for AuthnRequest.
     *
     * - [SamlBinding.HttpRedirect] (default): Uses HTTP redirect with URL query parameters
     * - [SamlBinding.HttpPost]: Uses HTTP POST with an auto-submit HTML form
     *
     * Default: [SamlBinding.HttpRedirect]
     */
    public var authnRequestBinding: SamlBinding = SamlBinding.HttpRedirect

    /**
     * Clock skew tolerance in seconds for timestamp validation.
     * Compensates for clock differences between SP and IdP.
     *
     * Default: 60 seconds
     */
    public var clockSkew: Duration = 60.seconds

    /**
     * Whether to require a signed SAML Response envelope from the IdP.
     *
     * When `true`, the entire SAML Response (not just the assertion) must be signed by the IdP.
     * This provides an additional layer of security by ensuring the response envelope
     * (including status, issuer, and destination) has not been tampered with.
     *
     * **When to enable:**
     * - When your IdP signs the Response in addition to (or instead of) the Assertion
     * - For defense-in-depth when both Response and Assertion signatures are available
     * - When required by your security policy or compliance requirements
     */
    public var requireSignedResponse: Boolean = false

    /**
     * Whether to require SAML LogoutRequests from the IdP to be signed.
     *
     * Default: true
     */
    public var requireSignedLogoutRequest: Boolean = true

    /**
     * Whether to allow IdP-initiated SSO (unsolicited responses).
     *
     * When `false` (default), only SP-initiated flows are allowed. The SP must first send an
     * AuthnRequest to the IdP, and the response must contain a valid `InResponseTo` attribute
     * matching the stored AuthnRequest ID.
     *
     * When `true`, the SP also accepts unsolicited SAML responses from the IdP (IdP-initiated SSO).
     * In this mode, responses without an `InResponseTo` attribute are accepted if they pass all
     * other validation checks.
     *
     * **Security note**: IdP-initiated SSO has additional security considerations:
     * - Increased CSRF attack surface (attacker can trigger authentication)
     * - No request-response binding validation
     * - User may be authenticated without explicit intent
     *
     * Only enable this if your IdP requires IdP-initiated SSO support.
     */
    public var allowIdpInitiatedSso: Boolean = false

    /**
     * List of allowed URL prefixes for RelayState redirection.
     *
     * When configured, the SP will only redirect to URLs that start with one of these prefixes
     * after successful authentication. This prevents open redirect attacks where an attacker
     * crafts a SAML response with a malicious RelayState parameter.
     *
     * By default, an empty list is used which blocks all external redirects (only relative paths
     * starting with "/" are allowed).
     *
     * To allow all RelayState URLs (UNSAFE, not recommended for production), set to `null`.
     *
     * **Example:**
     * ```kotlin
     * saml("saml-auth") {
     *     // Only allow redirects to same origin
     *     allowedRelayStateUrls = listOf("https://myapp.example.com/")
     *
     *     // Or allow multiple domains
     *     allowedRelayStateUrls = listOf(
     *         "https://myapp.example.com/",
     *         "https://app.example.com/"
     *     )
     * }
     * ```
     */
    public var allowedRelayStateUrls: List<String>? = emptyList()

    /**
     * Whether to require the Destination attribute in SAML responses.
     *
     * When `true` (default), SAML responses MUST include a `Destination` attribute that matches
     * the configured ACS URL. Responses without a `Destination` attribute will be rejected.
     * This provides protection against response injection attacks where an attacker redirects
     * a response intended for a different Service Provider.
     *
     * When `false`, the `Destination` attribute is optional. If present, it must match the
     * ACS URL; if absent, the response is accepted (per SAML 2.0 spec, Destination is optional).
     */
    public var requireDestination: Boolean = true

    /**
     * The requested NameID format in AuthnRequests.
     *
     * When non-null, AuthnRequests will include a NameIDPolicy element requesting
     * a provided NameID format. By default, NameIDPolicy element uses [NameIdFormat.Unspecified], allowing
     * the IdP to choose an appropriate format.
     *
     * When null, the NameIDPolicy element is omitted from AuthnRequests.
     *
     * @see NameIdFormat for available formats
     */
    public var nameIdFormat: NameIdFormat? = NameIdFormat.Unspecified

    /**
     * Whether to force re-authentication at the IdP.
     *
     * When `true`, the AuthnRequest will include `ForceAuthn="true"`, which instructs the IdP
     * to re-authenticate the user even if they have an existing session. This is useful for:
     * - Step-up authentication for sensitive operations
     * - Ensuring fresh authentication for high-security actions
     * - Compliance requirements that mandate explicit user authentication
     *
     * When `false` (default), the IdP may use an existing session to authenticate the user
     * without prompting for credentials.
     *
     * **Note:** Not all IdPs support or honor the ForceAuthn attribute. Check your IdP's
     * documentation for compatibility.
     */
    public var forceAuthn: Boolean = false

    /**
     * The signature algorithm to use when signing AuthnRequests.
     *
     * Common values:
     * - RSA with SHA-256 (default): [SignatureAlgorithm.RSA_SHA256]
     * - RSA with SHA-384: [SignatureAlgorithm.RSA_SHA384]
     * - RSA with SHA-512: [SignatureAlgorithm.RSA_SHA512]
     * - ECDSA with SHA-256: [SignatureAlgorithm.ECDSA_SHA256]
     *
     * **Note:** The algorithm must be supported by both the SP's KeyStore and the IdP.
     *
     * Default: RSA-SHA256
     */
    public var signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256

    /**
     * The digest algorithm to use when signing AuthnRequests.
     *
     * This is used in the reference digest computation during XML signature generation.
     *
     * Common values:
     * - SHA-256 (default): [DigestAlgorithm.SHA256]
     * - SHA-384: [DigestAlgorithm.SHA384]
     * - SHA-512: [DigestAlgorithm.SHA512]
     *
     * Default: SHA-256
     */
    public var digestAlgorithm: DigestAlgorithm = DigestAlgorithm.SHA256

    /**
     * Allowlist of signature algorithms for incoming SAML responses and assertions.
     *
     * Only signatures using these algorithms will be accepted. This prevents downgrade attacks
     * where an attacker forces the use of weak algorithms like SHA-1.
     *
     * Default: [SamlAlgorithms.RECOMMENDED_SIGNATURE_ALGORITHMS] (SHA-256 and above)
     *
     * To allow all algorithms (not recommended for production):
     * ```kotlin
     * saml("saml-auth") {
     *     allowedSignatureAlgorithms = null
     * }
     * ```
     */
    public var allowedSignatureAlgorithms: Set<SignatureAlgorithm>? =
        SamlAlgorithms.RECOMMENDED_SIGNATURE_ALGORITHMS

    /**
     * Allowlist of digest algorithms for signature references.
     *
     * Only digest algorithms in this allowlist will be accepted in signature references.
     * This prevents downgrade attacks where an attacker forces the use of weak digest
     * algorithms like MD5 or SHA-1.
     *
     * Default: [SamlAlgorithms.RECOMMENDED_DIGEST_ALGORITHMS] (SHA-256 and above)
     *
     * To allow all algorithms (not recommended for production):
     * ```kotlin
     * saml("saml-auth") {
     *     allowedDigestAlgorithms = null
     * }
     * ```
     */
    public var allowedDigestAlgorithms: Set<DigestAlgorithm>? =
        SamlAlgorithms.RECOMMENDED_DIGEST_ALGORITHMS

    /**
     * The requested AuthnContext class reference for authentication.
     *
     * This specifies the authentication method the SP requests from the IdP.
     * If `null` (default), no specific authentication context is requested and the IdP
     * uses its default authentication method.
     *
     * Common values:
     * - Password: `urn:oasis:names:tc:SAML:2.0:ac:classes:Password`
     * - Password with Protected Transport: `urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport`
     * - Multi-factor: `urn:oasis:names:tc:SAML:2.0:ac:classes:MultiFactor`
     * - Kerberos: `urn:oasis:names:tc:SAML:2.0:ac:classes:Kerberos`
     * - X.509 Certificate: `urn:oasis:names:tc:SAML:2.0:ac:classes:X509`
     *
     * **Note:** Not all IdPs support all authentication contexts. Check your IdP's documentation.
     *
     */
    public var requestedAuthnContext: SamlAuthnContext? = null

    internal var replayCache: SamlReplayCache? = null

    internal var authenticationFunction: AuthenticationFunction<SamlCredential>? = null

    /**
     * Challenge function to initiate SAML authentication flow.
     */
    internal var challengeFunction: SamlAuthChallengeFunction? = null

    /**
     * Sets a custom replay cache implementation.
     *
     * @param cache Custom SamlReplayCache implementation
     */
    public fun replayCache(cache: SamlReplayCache) {
        replayCache = cache
    }

    /**
     * Sets the authentication validation function.
     *
     * This function is called after the SAML assertion has been validated.
     * It receives a SamlCredential and should return a Principal if authentication succeeds,
     * or null if authentication fails.
     *
     * @param validate The validation function
     */
    public fun validate(validate: suspend ApplicationCall.(SamlCredential) -> Any?) {
        authenticationFunction = validate
    }

    /**
     * Sets the challenge function for initiating SAML authentication.
     *
     * @param block The challenge function
     */
    public fun challenge(block: SamlAuthChallengeFunction) {
        challengeFunction = block
    }
}

/**
 * Context for SAML authentication challenges.
 *
 * @property call The ApplicationCall that triggered the challenge
 */
public class SamlChallengeContext(
    public val call: ApplicationCall
)

/**
 * Type alias for SAML challenge functions.
 */
public typealias SamlAuthChallengeFunction = suspend SamlChallengeContext.(AuthenticationFailedCause) -> Unit
