/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.utils.io.*
import org.opensaml.saml.common.xml.SAMLConstants
import org.opensaml.saml.saml2.metadata.EntityDescriptor
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor
import org.opensaml.security.credential.Credential
import org.opensaml.security.credential.UsageType
import org.opensaml.security.x509.BasicX509Credential
import org.opensaml.xmlsec.signature.KeyInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

/**
 * Creates a new SAML Service Provider metadata configuration.
 *
 * This factory function allows creating a standalone [SamlSpMetadata] instance that can be
 * shared across different SAML configurations and operations (authentication, logout, metadata generation).
 *
 * ## Example Usage
 *
 * ```kotlin
 * val sp = SamlSpMetadata {
 *     spEntityId = "https://myapp.example.com/saml/metadata"
 *     acsUrl = "https://myapp.example.com/saml/acs"
 *     sloUrl = "https://myapp.example.com/saml/slo"
 *
 *     signingCredential = SamlCrypto.loadCredential(
 *         keystorePath = "/path/to/keystore.jks",
 *         keystorePassword = "password",
 *         keyAlias = "sp-key",
 *         keyPassword = "password"
 *     )
 *
 *     organizationName = "Example Corp"
 *     technicalContact {
 *         givenName = "John"
 *         emailAddress = "john@example.com"
 *     }
 * }
 *
 * // Use in authentication
 * install(Authentication) {
 *     saml("saml-auth") {
 *         sp = sp
 *         idp = parseSamlIdpMetadata(xmlString)
 *         validate { credential -> SamlPrincipal(credential.assertion) }
 *     }
 * }
 *
 * // Use for metadata generation
 * val metadataXml = sp.toXml()
 * ```
 *
 * @param configure Configuration block for SP metadata settings
 * @return A configured [SamlSpMetadata] instance
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.SamlSpMetadata)
 */
public fun SamlSpMetadata(configure: SamlSpMetadata.() -> Unit): SamlSpMetadata =
    SamlSpMetadata().apply(configure)

/**
 * Configuration for SAML Service Provider metadata.
 *
 * This class holds the SP metadata configuration that can be shared across
 * different SAML operations (authentication, logout, metadata generation).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.SamlSpMetadata)
 */
@KtorDsl
public class SamlSpMetadata internal constructor() {

    /**
     * The Service Provider (SP) entity ID.
     * This is a unique identifier for your application in the SAML federation.
     * Typically, a URL, but can be any unique string.
     */
    public var spEntityId: String? = null

    /**
     * The Assertion Consumer Service (ACS) URL.
     * This is the endpoint where the IdP will POST the SAML response after authentication.
     * Must be an absolute URL accessible from the user's browser.
     *
     * Default: "/saml/acs"
     */
    public var acsUrl: String = "/saml/acs"

    /**
     * The Single Logout (SLO) service URL.
     * This is the endpoint where the IdP will redirect/POST logout requests and responses.
     *
     * Default: "/saml/slo"
     */
    public var sloUrl: String = "/saml/slo"

    /**
     * Whether the SP wants assertions to be signed by the IdP.
     * This is advertised in the SP metadata and also used for runtime validation.
     *
     * Default: true
     */
    public var wantAssertionsSigned: Boolean = true

    /**
     * List of supported NameID formats for SP metadata.
     *
     * When generating SP metadata, these formats are advertised to the IdP.
     * If empty, no NameIDFormat elements will be included in the metadata.
     */
    public var supportedNameIdFormats: List<NameIdFormat> = emptyList()

    /**
     * The organization name for SP metadata.
     *
     * This value is included in the SP metadata XML when generating metadata
     * using [SamlSpMetadata.toXml].
     *
     * Optional - only included in metadata if set.
     */
    public var organizationName: String? = null

    /**
     * The organization display name for SP metadata.
     *
     * This is a human-readable name for the organization, typically longer than [organizationName].
     *
     * Optional - only included in metadata if set.
     */
    public var organizationDisplayName: String? = null

    /**
     * The organization URL for SP metadata.
     *
     * A URL pointing to the organization's website.
     *
     * Optional - only included in metadata if set.
     */
    public var organizationUrl: String? = null

    internal val technicalContacts = mutableListOf<SamlContactPerson>()
    internal val supportContacts = mutableListOf<SamlContactPerson>()

    /**
     * Adds a technical contact to the SP metadata.
     *
     * Technical contacts are typically IT staff who can be reached for
     * technical issues related to the SAML integration.
     *
     * ## Example
     * ```kotlin
     * SamlSpMetadata {
     *     technicalContact {
     *         givenName = "John"
     *         surname = "Doe"
     *         emailAddress = "john.doe@example.com"
     *     }
     * }
     * ```
     *
     * @param configure Configuration block for the contact
     */
    public fun technicalContact(configure: SamlContactPerson.() -> Unit) {
        technicalContacts.add(SamlContactPerson().apply(configure))
    }

    /**
     * Adds a support contact to the SP metadata.
     *
     * Support contacts are typically help desk or support staff who can help users with login issues.
     *
     * ## Example
     * ```kotlin
     * SamlSpMetadata {
     *     supportContact {
     *         givenName = "Help"
     *         surname = "Desk"
     *         emailAddress = "support@example.com"
     *     }
     * }
     * ```
     *
     * @param configure Configuration block for the contact
     */
    public fun supportContact(configure: SamlContactPerson.() -> Unit) {
        supportContacts.add(SamlContactPerson().apply(configure))
    }

    /**
     * The SP's signing credential containing a certificate and private key for signing SAML requests and metadata.
     *
     * Use [SamlCrypto.loadCredential] to load the credential from a KeyStore.
     *
     * ## Example
     * ```kotlin
     * SamlSpMetadata {
     *     signingCredential = SamlCrypto.loadCredential(
     *         keystorePath = "/path/to/keystore.jks",
     *         keystorePassword = "password",
     *         keyAlias = "sp-key",
     *         keyPassword = "password"
     *     )
     * }
     * ```
     *
     * Optional - only needed if you want to sign SAML requests or include a certificate in SP metadata.
     */
    public var signingCredential: BasicX509Credential? = null

    /**
     * The SP's encryption credential for decrypting SAML assertions.
     *
     * This credential is used to decrypt encrypted assertions from the IdP using RSA key transport (RSA-OAEP).
     * If not set, [signingCredential] will be used for decryption if it has an RSA key.
     * Use [SamlCrypto.loadCredential] to load the credential from a KeyStore.
     *
     * ## Example
     * ```kotlin
     * SamlSpMetadata {
     *     // RSA credential for encryption
     *     encryptionCredential = SamlCrypto.loadCredential(
     *         keystorePath = "/path/to/keystore.jks",
     *         keystorePassword = "password",
     *         keyAlias = "sp-encryption-key",
     *         keyPassword = "password"
     *     )
     * }
     * ```
     *
     * Optional - defaults to [signingCredential] if not set and the signing key supports encryption.
     */
    public var encryptionCredential: BasicX509Credential? = null
}

/**
 * Contact person information for SP metadata.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.SamlContactPerson)
 */
@KtorDsl
public class SamlContactPerson {
    /**
     * The contact's given name (first name).
     */
    public var givenName: String = ""

    /**
     * The contact's surname (last name).
     */
    public var surname: String = ""

    /**
     * The contact's email address.
     */
    public var emailAddress: String = ""

    /**
     * The contact's telephone number.
     */
    public var telephoneNumber: String = ""
}

/**
 * Represents parsed Identity Provider (IdP) metadata.
 *
 * This class extracts and holds the essential information from SAML metadata
 * needed for the SP to interact with the IdP.
 *
 * @property entityId The IdP's entity ID
 * @property ssoUrl The Single Sign-On service URL (default binding - HTTP-Redirect or HTTP-POST)
 * @property sloUrl The Single Logout service URL (default binding - HTTP-Redirect or HTTP-POST), null if not available
 */
public class IdPMetadata internal constructor(
    public val entityId: String,
    public val ssoUrl: String,
    public val sloUrl: String?,
    internal val signingCredentials: List<Credential>,
    private val ssoUrlRedirect: String? = null,
    private val ssoUrlPost: String? = null,
    private val sloUrlRedirect: String? = null,
    private val sloUrlPost: String? = null
) {
    /**
     * Returns the SSO URL for the specified binding.
     * Falls back to the default ssoUrl if binding-specific URL is not available.
     */
    internal fun getSsoUrlFor(binding: SamlBinding): String {
        return when (binding) {
            SamlBinding.HttpRedirect -> ssoUrlRedirect ?: ssoUrl
            SamlBinding.HttpPost -> ssoUrlPost ?: ssoUrl
        }
    }

    /**
     * Returns the SLO URL for the specified binding.
     * Falls back to the default sloUrl if binding-specific URL is not available.
     */
    internal fun getSloUrlFor(binding: SamlBinding): String? {
        return when (binding) {
            SamlBinding.HttpRedirect -> sloUrlRedirect ?: sloUrl
            SamlBinding.HttpPost -> sloUrlPost ?: sloUrl
        }
    }
}

private val CERT_EXPIRY_WARNING_THRESHOLD = 30.days

/**
 * Parses SAML IdP metadata from an XML string.
 *
 * @param xml The metadata XML content
 * @param validateCertificateExpiration Whether to validate the certificate expiration (default: true).
 *        When enabled, expired certificates will cause an exception and certificates expiring
 *        within 30 days will trigger a warning log.
 */
public fun parseSamlIdpMetadata(xml: String, validateCertificateExpiration: Boolean = true): IdPMetadata {
    LibSaml.ensureInitialized()
    val document: Document = LibSaml.parserPool.parse(ByteArrayInputStream(xml.toByteArray()))
    val entityDescriptor = document.documentElement.unmarshall<EntityDescriptor>()
    return entityDescriptor.extractIdPMetadata(validateCertificateExpiration)
}

private fun EntityDescriptor.extractIdPMetadata(validateCertificateExpiration: Boolean): IdPMetadata {
    val entityId = checkNotNull(entityID) { "EntityDescriptor must have an entityID" }
    val idpDescriptor = checkNotNull(getIDPSSODescriptor(SAMLConstants.SAML20P_NS)) {
        "No IDPSSODescriptor found in metadata"
    }

    val ssoEndpoints = idpDescriptor.singleSignOnServices

    // Extract binding-specific SSO endpoints
    val ssoUrlRedirect = ssoEndpoints
        .firstOrNull { it.binding == SAMLConstants.SAML2_REDIRECT_BINDING_URI }?.location
    val ssoUrlPost = ssoEndpoints
        .firstOrNull { it.binding == SAMLConstants.SAML2_POST_BINDING_URI }?.location

    // Default SSO endpoint (prefer Redirect, then POST)
    val ssoUrl = requireNotNull(ssoUrlRedirect ?: ssoUrlPost) {
        "No SingleSignOnService endpoint found in metadata"
    }

    val sloEndpoints = idpDescriptor.singleLogoutServices

    // Extract binding-specific SLO endpoints
    val sloUrlRedirect = sloEndpoints
        .firstOrNull { it.binding == SAMLConstants.SAML2_REDIRECT_BINDING_URI }?.location
    val sloUrlPost = sloEndpoints
        .firstOrNull { it.binding == SAMLConstants.SAML2_POST_BINDING_URI }?.location

    // Default SLO endpoint (prefer Redirect, then POST)
    val sloUrl = sloUrlRedirect ?: sloUrlPost

    // Extract signing certificates
    val signingCredentials = idpDescriptor.extractSigningCredentials(validateCertificateExpiration)
    require(signingCredentials.isNotEmpty()) {
        "No signing certificates found in IdP metadata. Signature verification will fail."
    }

    return IdPMetadata(
        entityId = entityId,
        ssoUrl = ssoUrl,
        sloUrl = sloUrl,
        signingCredentials = signingCredentials,
        ssoUrlRedirect = ssoUrlRedirect,
        ssoUrlPost = ssoUrlPost,
        sloUrlRedirect = sloUrlRedirect,
        sloUrlPost = sloUrlPost
    )
}

private fun IDPSSODescriptor.extractSigningCredentials(
    validateCertificateExpiration: Boolean
): List<Credential> = buildList {
    val parentId = (parent as? EntityDescriptor)?.entityID
    for (keyDescriptor in keyDescriptors) {
        // Use keys designated for signing or unspecified usage
        if (keyDescriptor.use != null && keyDescriptor.use != UsageType.SIGNING) {
            continue
        }
        val keyInfo = keyDescriptor.keyInfo ?: continue
        for (cert in keyInfo.extractCertificates(validateCertificateExpiration)) {
            val credential = BasicX509Credential(cert)
            // Set entity ID using the parent EntityDescriptor's entity ID
            parentId?.let { credential.entityId = it }
            add(credential)
        }
    }
}

private fun KeyInfo.extractCertificates(validateCertificateExpiration: Boolean): List<X509Certificate> {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    return buildList {
        x509Datas.forEach { x509Data ->
            x509Data.x509Certificates.forEach { x509CertificateType ->
                val certValue = x509CertificateType.value ?: return@forEach
                val certBytes = Base64.decode(source = certValue)
                val certInputStream = ByteArrayInputStream(certBytes)
                val cert = certificateFactory.generateCertificate(certInputStream) as X509Certificate
                if (validateCertificateExpiration) {
                    cert.validate()
                }
                add(cert)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun X509Certificate.validate() {
    checkValidity()

    // Warn if the certificate is expiring soon
    val expirationInstant = notAfter.toInstant().toKotlinInstant()
    val timeUntilExpiry = expirationInstant - Clock.System.now()

    if (timeUntilExpiry < CERT_EXPIRY_WARNING_THRESHOLD) {
        val daysUntilExpiry = timeUntilExpiry.inWholeDays
        LOGGER.warn(
            "IdP signing certificate is expiring soon! Subject: $subjectX500Principal, " +
                "Expires: $notAfter (in $daysUntilExpiry days). " +
                "Contact your IdP administrator to renew the certificate."
        )
    }
}

private val LOGGER: Logger = LoggerFactory.getLogger("io.ktor.server.auth.saml.IdPMetadata")
