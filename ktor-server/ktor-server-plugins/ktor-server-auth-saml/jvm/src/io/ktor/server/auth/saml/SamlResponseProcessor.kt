/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.saml.common.assertion.ValidationContext
import org.opensaml.saml.common.assertion.ValidationResult
import org.opensaml.saml.saml2.assertion.*
import org.opensaml.saml.saml2.assertion.impl.AudienceRestrictionConditionValidator
import org.opensaml.saml.saml2.assertion.impl.BearerSubjectConfirmationValidator
import org.opensaml.saml.saml2.core.Assertion
import org.opensaml.saml.saml2.core.EncryptedAssertion
import org.opensaml.saml.saml2.core.Response
import org.opensaml.saml.saml2.core.StatusCode
import org.opensaml.saml.saml2.encryption.Decrypter
import org.opensaml.saml.saml2.encryption.EncryptedElementTypeEncryptedKeyResolver
import org.opensaml.security.credential.Credential
import org.opensaml.xmlsec.encryption.support.ChainingEncryptedKeyResolver
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver
import org.opensaml.xmlsec.encryption.support.SimpleRetrievalMethodEncryptedKeyResolver
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import kotlin.io.encoding.Base64
import kotlin.time.*
import kotlin.time.Duration.Companion.minutes

/**
 * Maximum acceptable age for Response IssueInstant.
 */
private val RESPONSE_LIFETIME = 5.minutes

/**
 * Processes and validates SAML responses from the Identity Provider.
 *
 * This class handles the complete validation chain for SAML assertions:
 * 1. XML parsing with XXE protection
 * 2. Response unmarshalling
 * 3. Encrypted assertion decryption
 * 4. XML signature verification (XSW protection)
 * 5. Assertion semantic validation (timestamps, audience, subject confirmation)
 * 6. Replay attack detection
 */
@OptIn(ExperimentalTime::class)
internal class SamlResponseProcessor(
    private val spEntityId: String,
    private val acsUrl: String,
    private val idpMetadata: IdPMetadata,
    private val decryptionCredential: Credential?,
    private val clockSkew: Duration,
    private val replayCache: SamlReplayCache,
    private val requireSignedAssertions: Boolean,
    private val requireSignedResponse: Boolean,
    private val requireDestination: Boolean,
    private val allowIdpInitiatedSso: Boolean,
    private val signatureVerifier: SamlSignatureVerifier
) {
    init {
        LibSaml.ensureInitialized()
    }

    /**
     * SAML 2.0 Assertion validator using OpenSAML's built-in validation.
     *
     * This validator handles:
     * - IssueInstant validation (with clock skew)
     * - Conditions validation (NotBefore/NotOnOrAfter)
     * - Subject confirmation validation (Bearer method)
     * - Audience restriction validation
     * - Issuer validation
     * - Signature validation
     */
    private val assertionValidator: SAML20AssertionValidator by lazy {
        val conditionValidators = listOf<ConditionValidator>(
            AudienceRestrictionConditionValidator()
        )
        val subjectConfirmationValidators = listOf<SubjectConfirmationValidator>(
            BearerSubjectConfirmationValidator()
        )
        val statementValidators = emptyList<StatementValidator>()
        SAML20AssertionValidator(
            conditionValidators,
            subjectConfirmationValidators,
            statementValidators,
            null, // No generic assertion validator extension
            signatureVerifier.signatureTrustEngine,
            signatureVerifier.signatureProfileValidator
        )
    }

    /**
     * Processes a Base64-encoded SAML response.
     *
     * @param samlResponseBase64 The Base64-encoded SAML response from the POST parameter
     * @param expectedRequestId The ID of the AuthnRequest that initiated this flow (for InResponseTo validation)
     * @return SamlCredential containing the validated assertion
     * @throws SamlValidationException if validation fails
     */
    suspend fun processResponse(samlResponseBase64: String, expectedRequestId: String?): SamlCredential {
        val response = parseResponse(samlResponseBase64).also { it.validate(expectedRequestId) }
        val assertion = response.extractAssertion().also { it.validate(expectedRequestId) }
        return SamlCredential(response, assertion)
    }

    private fun parseResponse(samlResponseBase64: String): Response = withValidationException {
        val xmlStream = ByteArrayInputStream(Base64.decode(source = samlResponseBase64))
        val document: Document = LibSaml.parserPool.parse(xmlStream)
        document.documentElement.unmarshall<Response>()
    }

    private fun Response.validate(expectedRequestId: String?) {
        val statusCode = status?.statusCode?.value
        samlAssert(statusCode == StatusCode.SUCCESS) {
            val statusMessage = status?.statusMessage?.value ?: "No message"
            "SAML response status is not Success: $statusCode - $statusMessage"
        }

        if (expectedRequestId != null) {
            samlAssert(inResponseTo == expectedRequestId) { "InResponseTo mismatch" }
        } else {
            samlAssert(allowIdpInitiatedSso) { "IdP-initiated SSO is not allowed." }
        }

        samlAssert(issuer?.value == idpMetadata.entityId) { "Response issuer mismatch" }
        samlAssert(!requireDestination || destination != null) { "Response Destination is not present" }
        samlAssert(destination == null || destination == acsUrl) { "Response Destination mismatch" }

        val issueInstant = samlRequire(issueInstant?.toKotlinInstant()) { "Response IssueInstant is required" }

        val now = Clock.System.now()
        val effectiveMinTime = now - clockSkew - RESPONSE_LIFETIME
        val effectiveMaxTime = now + clockSkew

        samlAssert(issueInstant >= effectiveMinTime) { "Response IssueInstant is too old" }
        samlAssert(issueInstant <= effectiveMaxTime) { "Response IssueInstant is in the future" }

        if (requireSignedResponse) {
            signatureVerifier.verify(signedObject = this)
        }
    }

    /**
     * Extracts and decrypts the assertion from the response.
     */
    private fun Response.extractAssertion(): Assertion {
        return samlRequire(encryptedAssertions.firstOrNull()?.decrypt() ?: assertions.firstOrNull()) {
            "No assertion found in SAML response"
        }
    }

    private fun EncryptedAssertion.decrypt(): Assertion = withValidationException {
        val decryptionCredential = checkNotNull(decryptionCredential) {
            "No valid decryption credential is provided"
        }
        val kekResolver = StaticKeyInfoCredentialResolver(decryptionCredential)

        val encKeyResolvers = listOf(
            InlineEncryptedKeyResolver(),
            EncryptedElementTypeEncryptedKeyResolver(),
            SimpleRetrievalMethodEncryptedKeyResolver()
        )
        val encryptedKeyResolver = ChainingEncryptedKeyResolver(encKeyResolvers)

        val decrypter = Decrypter(null, kekResolver, encryptedKeyResolver).apply {
            isRootInNewDocument = true
        }
        decrypter.decrypt(this)
    }

    private suspend fun Assertion.validate(expectedRequestId: String?) {
        validateAssertionSemantics(expectedRequestId)
        checkReplay()
    }

    /**
     * Validates assertion semantics using OpenSAML's SAML20AssertionValidator.
     *
     * The validator handles:
     * - IssueInstant validation (with clock skew tolerance)
     * - Conditions validation (NotBefore / NotOnOrAfter)
     * - Subject confirmation validation (Bearer method with InResponseTo, Recipient, NotOnOrAfter)
     * - Audience restriction validation
     * - Issuer validation
     * - Signature validation (if [requireSignedAssertions] is true or assertion is signed)
     *
     * @throws SamlValidationException if validation fails
     */
    private fun Assertion.validateAssertionSemantics(expectedRequestId: String?) = withValidationException {
        val validationContext = buildValidationContext(expectedRequestId)

        val result = assertionValidator.validate(this, validationContext)
        samlAssert(result == ValidationResult.VALID) { "SAML assertion validation failed" }
    }

    /**
     * Builds a validation context for assertion validation.
     *
     * @return ValidationContext with required parameters
     */
    private fun buildValidationContext(expectedRequestId: String?): ValidationContext {
        val params = mutableMapOf<String, Any>()
        params[SAML2AssertionValidationParameters.CLOCK_SKEW] = clockSkew.toJavaDuration()
        params[SAML2AssertionValidationParameters.VALID_ISSUERS] = setOf(idpMetadata.entityId)
        params[SAML2AssertionValidationParameters.COND_VALID_AUDIENCES] = setOf(spEntityId)
        params[SAML2AssertionValidationParameters.SC_VALID_RECIPIENTS] = setOf(acsUrl)
        params[SAML2AssertionValidationParameters.SIGNATURE_REQUIRED] = requireSignedAssertions
        params[SAML2AssertionValidationParameters.SC_CHECK_ADDRESS] = false
        if (expectedRequestId != null) {
            params[SAML2AssertionValidationParameters.SC_VALID_IN_RESPONSE_TO] = expectedRequestId
            params[SAML2AssertionValidationParameters.SC_IN_RESPONSE_TO_REQUIRED] = true
        } else {
            params[SAML2AssertionValidationParameters.SC_IN_RESPONSE_TO_REQUIRED] = false
        }
        return ValidationContext(params)
    }

    /**
     * Checks if the assertion has been replayed (already processed).
     *
     * @throws SamlValidationException if this is a replay
     */
    private suspend fun Assertion.checkReplay() {
        val assertionId = samlRequire(id) { "Assertion must have an ID" }
        val expirationTime = conditions?.notOnOrAfter?.toKotlinInstant()
            ?: (Clock.System.now() + RESPONSE_LIFETIME)

        val recorded = replayCache.tryRecordAssertion(assertionId, expirationTime)
        samlAssert(recorded) {
            "Assertion has already been processed (replay attack)"
        }
    }
}

/**
 * Exception thrown when SAML validation fails.
 */
public class SamlValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
