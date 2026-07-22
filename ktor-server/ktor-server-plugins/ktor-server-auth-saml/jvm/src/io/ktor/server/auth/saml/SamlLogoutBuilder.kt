/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import org.opensaml.saml.saml2.core.*
import org.opensaml.security.credential.Credential
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

/**
 * Builds a LogoutRequest and returns the redirect URL for HTTP-Redirect binding.
 *
 * @param spEntityId The Service Provider's entity ID (Issuer)
 * @param idpSloUrl The IdP's Single Logout Service URL
 * @param nameId The NameID of the principal to log out
 * @param nameIdFormat The format of the NameID (optional)
 * @param sessionIndex The session index from the AuthnStatement (optional)
 * @param relayState Optional RelayState for post-logout redirect
 * @param signingCredential Credential for signing (if null, no signing is performed)
 * @param signatureAlgorithm Signature algorithm (default: RSA-SHA256)
 */
@OptIn(ExperimentalTime::class)
internal fun buildLogoutRequestRedirect(
    spEntityId: String,
    idpSloUrl: String,
    nameId: String,
    nameIdFormat: NameIdFormat? = null,
    sessionIndex: String? = null,
    relayState: String? = null,
    signingCredential: Credential? = null,
    signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256
): SamlRedirectResult {
    LibSaml.ensureInitialized()
    val logoutRequest = buildLogoutRequest(
        spEntityId = spEntityId,
        idpSloUrl = idpSloUrl,
        nameId = nameId,
        nameIdFormat = nameIdFormat,
        sessionIndex = sessionIndex
    )
    return buildSamlRedirectResult(
        messageId = checkNotNull(logoutRequest.id),
        samlObject = logoutRequest,
        destinationUrl = idpSloUrl,
        parameterName = "SAMLRequest",
        relayState = relayState,
        signingCredential = signingCredential,
        signatureAlgorithm = signatureAlgorithm
    )
}

@OptIn(ExperimentalTime::class)
private fun buildLogoutRequest(
    spEntityId: String,
    idpSloUrl: String,
    nameId: String,
    nameIdFormat: NameIdFormat?,
    sessionIndex: String?
): LogoutRequest {
    val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()
    val issuer = builderFactory.build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME) {
        value = spEntityId
    }
    val nameIdElement = builderFactory.build<NameID>(NameID.DEFAULT_ELEMENT_NAME) {
        value = nameId
        nameIdFormat?.let { format = it.uri }
    }
    val sessionIndexElement = sessionIndex?.let {
        builderFactory.build<SessionIndex>(SessionIndex.DEFAULT_ELEMENT_NAME) {
            value = it
        }
    }
    return builderFactory.build(LogoutRequest.DEFAULT_ELEMENT_NAME) {
        id = generateSecureSamlId()
        issueInstant = Clock.System.now().toJavaInstant()
        destination = idpSloUrl
        this.issuer = issuer
        this.nameID = nameIdElement
        sessionIndexElement?.let { sessionIndexes.add(it) }
    }
}

/**
 * Builds a LogoutResponse and returns the redirect URL for HTTP-Redirect binding.
 *
 * @param spEntityId The Service Provider's entity ID (Issuer)
 * @param idpSloUrl The IdP's Single Logout Service URL
 * @param inResponseTo The ID of the LogoutRequest this is responding to
 * @param statusCodeValue The status code (default: SUCCESS)
 * @param relayState Optional RelayState for post-logout redirect
 * @param signingCredential Credential for signing (if null, no signing is performed)
 * @param signatureAlgorithm Signature algorithm (default: RSA-SHA256)
 */
@OptIn(ExperimentalTime::class)
internal fun buildLogoutResponseRedirect(
    spEntityId: String,
    idpSloUrl: String,
    inResponseTo: String,
    statusCodeValue: String = StatusCode.SUCCESS,
    relayState: String? = null,
    signingCredential: Credential? = null,
    signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256
): SamlRedirectResult {
    LibSaml.ensureInitialized()
    val logoutResponse = buildLogoutResponse(
        spEntityId = spEntityId,
        idpSloUrl = idpSloUrl,
        inResponseTo = inResponseTo,
        statusCodeValue = statusCodeValue
    )
    return buildSamlRedirectResult(
        messageId = checkNotNull(logoutResponse.id),
        samlObject = logoutResponse,
        destinationUrl = idpSloUrl,
        parameterName = "SAMLResponse",
        relayState = relayState,
        signingCredential = signingCredential,
        signatureAlgorithm = signatureAlgorithm
    )
}

@OptIn(ExperimentalTime::class)
private fun buildLogoutResponse(
    spEntityId: String,
    idpSloUrl: String,
    inResponseTo: String,
    statusCodeValue: String
): LogoutResponse {
    val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()
    val issuer = builderFactory.build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME) {
        value = spEntityId
    }
    val statusCode = builderFactory.build<StatusCode>(StatusCode.DEFAULT_ELEMENT_NAME) {
        value = statusCodeValue
    }
    val status = builderFactory.build<Status>(Status.DEFAULT_ELEMENT_NAME) {
        this.statusCode = statusCode
    }
    return builderFactory.build(LogoutResponse.DEFAULT_ELEMENT_NAME) {
        this.id = generateSecureSamlId()
        this.issueInstant = Clock.System.now().toJavaInstant()
        this.destination = idpSloUrl
        this.inResponseTo = inResponseTo
        this.issuer = issuer
        this.status = status
    }
}
