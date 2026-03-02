/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.saml.saml2.core.LogoutRequest
import org.opensaml.saml.saml2.core.LogoutResponse
import org.opensaml.saml.saml2.core.StatusCode
import org.w3c.dom.Document
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Result of processing a SAML LogoutRequest.
 *
 * @property requestId The ID of the LogoutRequest
 * @property nameId The NameID of the subject to log out
 * @property sessionIndex The session index to log out (optional)
 */
internal class LogoutRequestResult(
    val requestId: String,
    val nameId: String,
    val sessionIndex: String?
)

/**
 * Result of processing a SAML LogoutResponse.
 *
 * @property statusCode The SAML status code
 * @property statusMessage Optional status message from the IdP
 * @property inResponseTo The ID of the LogoutRequest this response to
 */
internal class LogoutResult(
    val statusCode: String,
    val statusMessage: String?,
    val inResponseTo: String?
) {
    val isSuccess: Boolean get() = statusCode == StatusCode.SUCCESS
}

/**
 * Maximum acceptable age for LogoutRequest/LogoutResponse IssueInstant.
 */
private val LOGOUT_MESSAGE_LIFETIME = 5.minutes

/**
 * Processor for SAML LogoutRequest and LogoutResponse messages.
 *
 * This class validates and extracts information from SAML 2.0 logout
 * messages received from the IdP during Single Logout (SLO).
 *
 * ## Security Features
 *
 * - Issuer validation (required by default)
 * - IssueInstant freshness validation with configurable clock skew
 * - Replay attack protection for LogoutRequest IDs
 * - Signature verification (configurable)
 * - Destination validation
 */
@OptIn(ExperimentalTime::class)
internal class SamlLogoutProcessor(
    private val sloUrl: String,
    private val idpMetadata: IdPMetadata,
    private val requireSignedLogoutRequest: Boolean,
    private val requireSignedLogoutResponse: Boolean,
    private val requireDestination: Boolean,
    private val signatureVerifier: SamlSignatureVerifier,
    private val clockSkew: Duration,
    private val replayCache: SamlReplayCache,
) {
    init {
        LibSaml.ensureInitialized()
    }

    /**
     * Processes a Base64-encoded SAML LogoutRequest.
     *
     * @param samlRequestBase64 The Base64-encoded LogoutRequest (deflated for HTTP-Redirect binding)
     * @param binding The SAML binding used (HTTP-Redirect or HTTP-POST)
     * @param queryString The raw query string for HTTP-Redirect binding signature verification.
     *        Must preserve the exact encoding from the IdP. The Signature parameter will be removed internally.
     * @return LogoutRequestResult containing the request ID, nameId, and sessionIndex
     * @throws SamlValidationException if the request is malformed or invalid
     */
    suspend fun processRequest(
        samlRequestBase64: String,
        binding: SamlBinding,
        queryString: String? = null,
        signatureParam: String? = null,
        signatureAlgorithmParam: String? = null
    ): LogoutRequestResult {
        val logoutRequest = withValidationException {
            val isDeflated = binding == SamlBinding.HttpRedirect
            val requestXml = samlRequestBase64.decodeSamlMessage(isDeflated)

            val document: Document = LibSaml.parserPool.parse(requestXml.toByteArray().inputStream())
            document.documentElement.unmarshall<LogoutRequest>()
        }

        val requestId = samlRequire(logoutRequest.id) { "LogoutRequest must have an ID" }

        // Issuer is required for security - ensures the request is from the expected IdP
        val issuer = samlRequire(logoutRequest.issuer?.value) { "LogoutRequest Issuer is required" }
        samlAssert(issuer == idpMetadata.entityId) { "Issuer mismatch" }

        // Validate IssueInstant freshness
        val issueInstant = samlRequire(logoutRequest.issueInstant?.toKotlinInstant()) {
            "LogoutRequest IssueInstant is required"
        }
        validateIssueInstant(issueInstant, "LogoutRequest")

        val expirationTime = Clock.System.now() + LOGOUT_MESSAGE_LIFETIME + clockSkew
        val recorded = replayCache.tryRecordAssertion(assertionId = requestId, expirationTime)
        samlAssert(recorded) {
            "LogoutRequest has already been processed (replay attack)"
        }

        val destination = logoutRequest.destination
        samlAssert(!requireDestination || destination != null) { "LogoutRequest Destination is not present" }
        samlAssert(destination == null || destination == sloUrl) { "Destination mismatch" }

        if (requireSignedLogoutRequest) {
            if (binding == SamlBinding.HttpRedirect) {
                signatureVerifier.verifyQueryString(
                    queryString = checkNotNull(queryString),
                    signatureBase64 = samlRequire(signatureParam) { "Signature is missing" },
                    signatureAlgorithmUri = samlRequire(signatureAlgorithmParam) { "SigAlg is missing" }
                )
            } else {
                signatureVerifier.verify(signedObject = logoutRequest)
            }
        }

        val nameId = samlRequire(logoutRequest.nameID?.value) { "LogoutRequest must contain a NameID" }

        val sessionIndex = logoutRequest.sessionIndexes.firstOrNull()?.value

        return LogoutRequestResult(
            requestId = requestId,
            nameId = nameId,
            sessionIndex = sessionIndex
        )
    }

    /**
     * Processes a Base64-encoded SAML LogoutResponse.
     *
     * @param samlResponseBase64 The Base64-encoded LogoutResponse (deflated for HTTP-Redirect binding)
     * @param expectedRequestId The ID of the LogoutRequest that was sent (for InResponseTo validation)
     * @param binding The SAML binding used (HTTP-Redirect or HTTP-POST)
     * @param queryString The raw query string for HTTP-Redirect binding signature verification.
     * @throws SamlValidationException if the response is malformed or invalid
     */
    fun processResponse(
        samlResponseBase64: String,
        expectedRequestId: String?,
        binding: SamlBinding,
        queryString: String? = null,
        signatureParam: String? = null,
        signatureAlgorithmParam: String? = null
    ): LogoutResult {
        val responseXml = samlResponseBase64.decodeSamlMessage(isDeflated = binding == SamlBinding.HttpRedirect)
        val document: Document = LibSaml.parserPool.parse(responseXml.toByteArray().inputStream())
        val logoutResponse = document.documentElement.unmarshall<LogoutResponse>()

        val inResponseTo = logoutResponse.inResponseTo
        samlAssert(expectedRequestId == null || inResponseTo == expectedRequestId) { "InResponseTo mismatch" }

        // Issuer is required for security - ensures the response is from the expected IdP
        val issuer = samlRequire(logoutResponse.issuer?.value) { "LogoutResponse Issuer is required" }
        samlAssert(issuer == idpMetadata.entityId) { "Issuer mismatch" }

        // Validate IssueInstant freshness
        val issueInstant = samlRequire(logoutResponse.issueInstant?.toKotlinInstant()) {
            "LogoutResponse IssueInstant is required"
        }
        validateIssueInstant(issueInstant, "LogoutResponse")

        val destination = logoutResponse.destination
        samlAssert(!requireDestination || destination != null) { "LogoutResponse Destination is not present" }
        samlAssert(destination == null || destination == sloUrl) { "Destination mismatch" }

        if (requireSignedLogoutResponse) {
            if (binding == SamlBinding.HttpRedirect) {
                signatureVerifier.verifyQueryString(
                    queryString = checkNotNull(queryString),
                    signatureBase64 = samlRequire(signatureParam) { "Signature is missing" },
                    signatureAlgorithmUri = samlRequire(signatureAlgorithmParam) { "SigAlg is missing" },
                )
            } else {
                signatureVerifier.verify(signedObject = logoutResponse)
            }
        }

        val status = samlRequire(logoutResponse.status) { "LogoutResponse has no Status element" }
        val statusCode = samlRequire(status.statusCode?.value) { "LogoutResponse Status has no StatusCode" }
        val statusMessage = status.statusMessage?.value

        return LogoutResult(statusCode, statusMessage, inResponseTo)
    }

    private fun validateIssueInstant(issueInstant: Instant, messageType: String) {
        val now = Clock.System.now()
        val effectiveMinTime = now - clockSkew - LOGOUT_MESSAGE_LIFETIME
        val effectiveMaxTime = now + clockSkew

        samlAssert(issueInstant >= effectiveMinTime) { "$messageType IssueInstant is too old" }
        samlAssert(issueInstant <= effectiveMaxTime) { "$messageType IssueInstant is in the future" }
    }
}
