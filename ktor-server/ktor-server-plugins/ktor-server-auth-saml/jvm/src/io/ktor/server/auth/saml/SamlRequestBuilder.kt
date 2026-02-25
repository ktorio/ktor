/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.util.*
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import org.opensaml.saml.common.xml.SAMLConstants
import org.opensaml.saml.saml2.core.*
import org.opensaml.security.credential.Credential
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory
import org.opensaml.xmlsec.signature.Signature
import org.opensaml.xmlsec.signature.support.SignatureConstants
import org.opensaml.xmlsec.signature.support.Signer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

private val DEFAULT_SIGNATURE_ALGORITHM = SignatureAlgorithm.RSA_SHA256
private val DEFAULT_DIGEST_ALGORITHM = DigestAlgorithm.SHA256

/**
 * Builds an AuthnRequest and generates a redirect URL to the IdP.
 *
 * @param spEntityId The Service Provider entity ID
 * @param acsUrl The Assertion Consumer Service URL
 * @param idpSsoUrl The IdP's Single Sign-On URL
 * @param relayState Optional RelayState parameter (original requested URL)
 * @param signingCredential Credential for signing (if null, no signing is performed)
 * @param nameIdFormat Optional NameID format to request (e.g., email, persistent)
 * @param forceAuthn Whether to force re-authentication at the IdP
 * @param signatureAlgorithm Signature algorithm to use for signing
 * @param requestedAuthnContext Optional requested authentication context
 */
internal fun buildAuthnRequestRedirect(
    spEntityId: String,
    acsUrl: String,
    idpSsoUrl: String,
    relayState: String? = null,
    signingCredential: Credential? = null,
    nameIdFormat: NameIdFormat? = null,
    forceAuthn: Boolean = false,
    signatureAlgorithm: SignatureAlgorithm = DEFAULT_SIGNATURE_ALGORITHM,
    requestedAuthnContext: SamlAuthnContext? = null
): SamlRedirectResult {
    LibSaml.ensureInitialized()
    val authnRequest = buildAuthnRequest(
        spEntityId,
        acsUrl,
        destination = idpSsoUrl,
        nameIdFormat,
        forceAuthn,
        requestedAuthnContext
    )
    return buildSamlRedirectResult(
        messageId = checkNotNull(authnRequest.id),
        samlObject = authnRequest,
        destinationUrl = idpSsoUrl,
        parameterName = "SAMLRequest",
        relayState = relayState,
        signingCredential = signingCredential,
        signatureAlgorithm = signatureAlgorithm
    )
}

/**
 * Builds an AuthnRequest and returns the data for HTTP-POST binding.
 *
 * In HTTP-POST binding, the AuthnRequest is:
 * - Signed using XML Signature (embedded in the document) if signingCredential is provided
 * - Base64-encoded (without deflation)
 * - Sent in an HTML form that auto-submits via JavaScript
 */
internal fun buildAuthnRequestPost(
    spEntityId: String,
    acsUrl: String,
    idpSsoUrl: String,
    relayState: String? = null,
    signingCredential: Credential? = null,
    nameIdFormat: NameIdFormat? = null,
    forceAuthn: Boolean = false,
    signatureAlgorithm: SignatureAlgorithm = DEFAULT_SIGNATURE_ALGORITHM,
    digestAlgorithm: DigestAlgorithm = DEFAULT_DIGEST_ALGORITHM,
    requestedAuthnContext: SamlAuthnContext? = null
): AuthnRequestPostData {
    LibSaml.ensureInitialized()
    val authnRequest = buildAuthnRequest(
        spEntityId,
        acsUrl,
        destination = idpSsoUrl,
        nameIdFormat,
        forceAuthn,
        requestedAuthnContext
    )
    val requestId = checkNotNull(authnRequest.id)

    if (signingCredential != null) {
        authnRequest.addSignature(signingCredential, signatureAlgorithm)
    }

    var authnRequestXml = authnRequest.marshalToString()
    if (signingCredential != null) {
        Signer.signObject(checkNotNull(authnRequest.signature))
        // Re-marshal to include the computed signature value
        authnRequestXml = authnRequest.marshalToString()
    }

    val encodedRequest = Base64.encode(source = authnRequestXml.toByteArray(Charsets.UTF_8))

    return AuthnRequestPostData(
        requestId = requestId,
        idpSsoUrl = idpSsoUrl,
        samlRequest = encodedRequest,
        relayState = relayState
    )
}

/**
 * Adds a Signature element to an AuthnRequest for HTTP-POST binding.
 * The actual signing (computing the signature value) must happen after marshaling.
 */
private fun AuthnRequest.addSignature(credential: Credential, signatureAlgorithm: SignatureAlgorithm) {
    val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

    val keyInfoGeneratorFactory = X509KeyInfoGeneratorFactory()
    keyInfoGeneratorFactory.setEmitEntityCertificate(true)
    val keyInfoGenerator = keyInfoGeneratorFactory.newInstance()

    this.signature = builderFactory.buildXmlObject(Signature.DEFAULT_ELEMENT_NAME) {
        this.signingCredential = credential
        this.signatureAlgorithm = signatureAlgorithm.uri
        this.keyInfo = keyInfoGenerator.generate(credential)
        this.canonicalizationAlgorithm = SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
    }
}

@OptIn(ExperimentalTime::class)
private fun buildAuthnRequest(
    spEntityId: String,
    acsUrl: String,
    destination: String,
    nameIdFormat: NameIdFormat?,
    forceAuthn: Boolean,
    requestedAuthnContext: SamlAuthnContext?
): AuthnRequest {
    val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()
    val issuer = builderFactory.build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME) {
        value = spEntityId
    }

    val nameIDPolicy = nameIdFormat?.let { format ->
        builderFactory.build<NameIDPolicy>(NameIDPolicy.DEFAULT_ELEMENT_NAME) {
            this.format = format.uri
            this.allowCreate = true
        }
    }

    val reqAuthnContext = requestedAuthnContext?.let { requestedAuthnContext ->
        val ref = builderFactory.build<AuthnContextClassRef>(AuthnContextClassRef.DEFAULT_ELEMENT_NAME) {
            this.uri = requestedAuthnContext.uri
        }

        builderFactory.build<RequestedAuthnContext>(RequestedAuthnContext.DEFAULT_ELEMENT_NAME) {
            this.authnContextClassRefs.add(ref)
            this.comparison = AuthnContextComparisonTypeEnumeration.EXACT
        }
    }

    return builderFactory.build(AuthnRequest.DEFAULT_ELEMENT_NAME) {
        this.issuer = issuer
        this.destination = destination
        this.nameIDPolicy = nameIDPolicy
        this.id = generateSecureSamlId()
        this.assertionConsumerServiceURL = acsUrl
        this.issueInstant = Clock.System.now().toJavaInstant()
        this.protocolBinding = SAMLConstants.SAML2_POST_BINDING_URI
        reqAuthnContext?.let { this.requestedAuthnContext = it }
        if (forceAuthn) {
            this.isForceAuthn = true
        }
    }
}

/**
 * AuthnRequest encoded for HTTP-POST binding.
 *
 * @property requestId The ID of the AuthnRequest (for correlating the response)
 * @property idpSsoUrl The IdP's SSO URL (form action)
 * @property samlRequest The Base64-encoded SAMLRequest
 * @property relayState Optional RelayState for the IdP to return
 */
internal class AuthnRequestPostData(
    val requestId: String,
    val idpSsoUrl: String,
    val samlRequest: String,
    val relayState: String?
) {
    /**
     * Generates an auto-submit HTML form for the HTTP-POST binding.
     *
     * The form will automatically submit when loaded (requires JavaScript).
     * A Submit button is included as a fallback for users without JavaScript.
     */
    fun toAutoSubmitHtml(): String {
        val relayStateInput = if (relayState != null) {
            """<input type="hidden" name="RelayState" value="${relayState.escapeHTML()}"/>"""
        } else {
            ""
        }
        return """
            |<!DOCTYPE html>
            |<html>
            |<head>
            |    <meta charset="UTF-8">
            |    <title>Redirecting to Identity Provider...</title>
            |</head>
            |<body onload="document.forms[0].submit()">
            |    <noscript>
            |        <p>JavaScript is disabled. Please click the button below to continue.</p>
            |    </noscript>
            |    <form method="POST" action="${idpSsoUrl.escapeHTML()}">
            |        <input type="hidden" name="SAMLRequest" value="${samlRequest.escapeHTML()}"/>
            |        $relayStateInput
            |        <noscript><button type="submit">Continue to Identity Provider</button></noscript>
            |    </form>
            |</body>
            |</html>
        """.trimMargin()
    }
}
