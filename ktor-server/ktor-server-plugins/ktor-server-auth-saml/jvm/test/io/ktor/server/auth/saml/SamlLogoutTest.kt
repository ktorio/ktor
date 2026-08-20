/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.network.tls.certificates.buildKeyStore
import kotlinx.coroutines.runBlocking
import org.opensaml.saml.saml2.core.StatusCode
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Tests for SAML Single Logout (SLO) functionality
 */
@OptIn(ExperimentalTime::class)
class SamlLogoutTest {

    @Test
    fun `build logout request redirect with and without signing`() {
        val credentials = SamlTestUtils.generateTestCredentials()

        val unsignedResult = buildLogoutRequestRedirect(
            spEntityId = "https://sp.example.com",
            idpSloUrl = "https://idp.example.com/saml/slo",
            nameId = "user@example.com",
            nameIdFormat = NameIdFormat.Email,
            sessionIndex = "_session123",
            relayState = "/dashboard",
            signingCredential = null
        )
        assertNotNull(unsignedResult.messageId)
        assertTrue(unsignedResult.messageId.startsWith("_"))
        assertTrue(unsignedResult.redirectUrl.startsWith("https://idp.example.com/saml/slo"))
        assertTrue(unsignedResult.redirectUrl.contains("SAMLRequest="))
        assertTrue(unsignedResult.redirectUrl.contains("RelayState="))
        assertFalse(unsignedResult.redirectUrl.contains("Signature="))

        val signedResult = buildLogoutRequestRedirect(
            spEntityId = "https://sp.example.com",
            idpSloUrl = "https://idp.example.com/saml/slo",
            nameId = "user@example.com",
            sessionIndex = null,
            signingCredential = credentials.credential
        )
        assertNotNull(signedResult.messageId)
        assertTrue(signedResult.redirectUrl.contains("SAMLRequest="))
        assertTrue(signedResult.redirectUrl.contains("SigAlg="))
        assertTrue(signedResult.redirectUrl.contains("Signature="))
    }

    @Test
    fun `process logout response with different status codes`() {
        val idpMetadata = SamlTestUtils.createTestIdPMetadata()
        val processor = createProcessor(idpMetadata)

        val successResponse = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_request123",
            statusCode = StatusCode.SUCCESS,
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo"
        )
        val successResult = processor.processResponse(
            samlResponseBase64 = SamlTestUtils.encodeForPost(successResponse),
            expectedRequestId = "_request123",
            binding = SamlBinding.HttpPost
        )
        assertTrue(successResult.isSuccess)
        assertEquals(StatusCode.SUCCESS, successResult.statusCode)
        assertEquals("_request123", successResult.inResponseTo)

        val failedResponse = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_request456",
            statusCode = StatusCode.RESPONDER,
            statusMessage = "Logout failed at IdP",
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo"
        )
        val failedResult = processor.processResponse(
            samlResponseBase64 = SamlTestUtils.encodeForPost(failedResponse),
            expectedRequestId = "_request456",
            binding = SamlBinding.HttpPost
        )
        assertFalse(failedResult.isSuccess)
        assertEquals(StatusCode.RESPONDER, failedResult.statusCode)
        assertEquals("Logout failed at IdP", failedResult.statusMessage)
    }

    @Test
    fun `process logout response validation failures`() {
        val idpMetadata = SamlTestUtils.createTestIdPMetadata()
        val processor = createProcessor(idpMetadata)

        val mismatchedInResponseTo = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_different_request",
            statusCode = StatusCode.SUCCESS,
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo"
        )
        assertFailsWith<SamlValidationException> {
            processor.processResponse(
                samlResponseBase64 = SamlTestUtils.encodeForPost(mismatchedInResponseTo),
                expectedRequestId = "_request123",
                binding = SamlBinding.HttpPost
            )
        }

        val wrongIssuer = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_request123",
            statusCode = StatusCode.SUCCESS,
            issuer = "https://malicious-idp.example.com",
            destination = "https://sp.example.com/saml/slo"
        )
        assertFailsWith<SamlValidationException> {
            processor.processResponse(
                samlResponseBase64 = SamlTestUtils.encodeForPost(wrongIssuer),
                expectedRequestId = "_request123",
                binding = SamlBinding.HttpPost
            )
        }
    }

    @Test
    fun `IdP metadata SLO URL parsing`() {
        val withSloUrl = parseSamlIdpMetadata(
            """
            <?xml version="1.0"?>
            <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata"
                              xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                              entityID="https://idp.example.com">
                <IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <KeyDescriptor use="signing">
                        <ds:KeyInfo>
                            <ds:X509Data>
                                <ds:X509Certificate>$TEST_CERTIFICATE_BASE64</ds:X509Certificate>
                            </ds:X509Data>
                        </ds:KeyInfo>
                    </KeyDescriptor>
                    <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://idp.example.com/sso"/>
                    <SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://idp.example.com/slo"/>
                    <SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://idp.example.com/slo-post"/>
                </IDPSSODescriptor>
            </EntityDescriptor>
            """.trimIndent(),
            validateCertificateExpiration = false
        )
        assertEquals("https://idp.example.com", withSloUrl.entityId)
        assertEquals("https://idp.example.com/sso", withSloUrl.ssoUrl)
        assertEquals("https://idp.example.com/slo", withSloUrl.sloUrl)

        val withoutSloUrl = parseSamlIdpMetadata(
            """
            <?xml version="1.0"?>
            <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata"
                              xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                              entityID="https://idp.example.com">
                <IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <KeyDescriptor use="signing">
                        <ds:KeyInfo>
                            <ds:X509Data>
                                <ds:X509Certificate>$TEST_CERTIFICATE_BASE64</ds:X509Certificate>
                            </ds:X509Data>
                        </ds:KeyInfo>
                    </KeyDescriptor>
                    <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://idp.example.com/sso"/>
                </IDPSSODescriptor>
            </EntityDescriptor>
            """.trimIndent(),
            validateCertificateExpiration = false
        )
        assertEquals("https://idp.example.com", withoutSloUrl.entityId)
        assertEquals("https://idp.example.com/sso", withoutSloUrl.ssoUrl)
        assertNull(withoutSloUrl.sloUrl)
    }

    @Test
    fun `HTTP-Redirect signature verification for requests and responses`() = runBlocking {
        val credentials = SamlTestUtils.sharedIdpCredentials
        val idpMetadata = SamlTestUtils.createTestIdPMetadataWithSlo(credentials = credentials)
        val processor = SamlLogoutProcessor(
            sloUrl = "https://sp.example.com/saml/slo",
            idpMetadata = idpMetadata,
            requireSignedLogoutRequest = true,
            requireSignedLogoutResponse = true,
            requireDestination = true,
            signatureVerifier = SamlSignatureVerifier(idpMetadata),
            clockSkew = 60.seconds,
            replayCache = InMemorySamlReplayCache()
        )

        val signedRequest = SamlTestUtils.createSignedLogoutRequestRedirect(
            credentials = credentials,
            issuer = idpMetadata.entityId!!,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com",
            sessionIndex = "_session123"
        )
        val requestResult = processor.processRequest(
            samlRequestBase64 = signedRequest.samlMessageBase64,
            binding = SamlBinding.HttpRedirect,
            queryString = signedRequest.fullQueryString,
            signatureParam = signedRequest.signatureBase64,
            signatureAlgorithmParam = signedRequest.signatureAlgorithmUri
        )
        assertEquals("user@example.com", requestResult.nameId)
        assertEquals("_session123", requestResult.sessionIndex)

        val signedResponse = SamlTestUtils.createSignedLogoutResponseRedirect(
            credentials = credentials,
            inResponseTo = "_request123",
            statusCode = StatusCode.SUCCESS,
            issuer = idpMetadata.entityId!!,
            destination = "https://sp.example.com/saml/slo"
        )
        val responseResult = processor.processResponse(
            samlResponseBase64 = signedResponse.samlMessageBase64,
            expectedRequestId = "_request123",
            binding = SamlBinding.HttpRedirect,
            queryString = signedResponse.fullQueryString,
            signatureParam = signedResponse.signatureBase64,
            signatureAlgorithmParam = signedResponse.signatureAlgorithmUri
        )
        assertTrue(responseResult.isSuccess)
        assertEquals("_request123", responseResult.inResponseTo)
    }

    @Test
    fun `signature verification failures`(): Unit = runBlocking {
        val signingCredentials = SamlTestUtils.generateTestCredentials()
        val verificationCredentials = SamlTestUtils.generateTestCredentials()
        val idpMetadata = SamlTestUtils.createTestIdPMetadataWithSlo(credentials = verificationCredentials)
        val processor = SamlLogoutProcessor(
            sloUrl = "https://sp.example.com/saml/slo",
            idpMetadata = idpMetadata,
            requireSignedLogoutRequest = true,
            requireSignedLogoutResponse = true,
            requireDestination = true,
            signatureVerifier = SamlSignatureVerifier(idpMetadata),
            clockSkew = 60.seconds,
            replayCache = InMemorySamlReplayCache()
        )

        val wrongKey = SamlTestUtils.createSignedLogoutRequestRedirect(
            credentials = signingCredentials,
            issuer = idpMetadata.entityId!!,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com"
        )
        assertFailsWith<SamlValidationException> {
            processor.processRequest(
                samlRequestBase64 = wrongKey.samlMessageBase64,
                binding = SamlBinding.HttpRedirect,
                queryString = wrongKey.fullQueryString
            )
        }

        val validCredentials = SamlTestUtils.sharedIdpCredentials
        val validMetadata = SamlTestUtils.createTestIdPMetadataWithSlo(credentials = validCredentials)
        val validProcessor = SamlLogoutProcessor(
            sloUrl = "https://sp.example.com/saml/slo",
            idpMetadata = validMetadata,
            requireSignedLogoutRequest = true,
            requireSignedLogoutResponse = true,
            requireDestination = true,
            signatureVerifier = SamlSignatureVerifier(validMetadata),
            clockSkew = 60.seconds,
            replayCache = InMemorySamlReplayCache()
        )
        val signedMessage = SamlTestUtils.createSignedLogoutRequestRedirect(
            credentials = validCredentials,
            issuer = validMetadata.entityId!!,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com"
        )
        val tamperedQueryString = signedMessage.fullQueryString.replace("&Signature=", "&extra=param&Signature=")
        assertFailsWith<SamlValidationException> {
            validProcessor.processRequest(
                samlRequestBase64 = signedMessage.samlMessageBase64,
                binding = SamlBinding.HttpRedirect,
                queryString = tamperedQueryString
            )
        }
    }

    @Test
    fun `missing Issuer is rejected`(): Unit = runBlocking {
        val idpMetadata = SamlTestUtils.createTestIdPMetadata()
        val processor = createProcessor(idpMetadata)

        val requestWithoutIssuer = SamlTestUtils.createLogoutRequest(
            issuer = null,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com"
        )
        val requestException = assertFailsWith<SamlValidationException> {
            processor.processRequest(
                samlRequestBase64 = SamlTestUtils.encodeForPost(requestWithoutIssuer),
                binding = SamlBinding.HttpPost
            )
        }
        assertTrue(requestException.message!!.contains("Issuer is required"))

        val responseWithoutIssuer = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_request123",
            statusCode = StatusCode.SUCCESS,
            issuer = null,
            destination = "https://sp.example.com/saml/slo"
        )
        assertFailsWith<SamlValidationException> {
            processor.processResponse(
                samlResponseBase64 = SamlTestUtils.encodeForPost(responseWithoutIssuer),
                expectedRequestId = "_request123",
                binding = SamlBinding.HttpPost
            )
        }
    }

    @Test
    fun `IssueInstant validation for LogoutRequest`() = runBlocking {
        val idpMetadata = SamlTestUtils.createTestIdPMetadata()
        val processor = SamlLogoutProcessor(
            sloUrl = "https://sp.example.com/saml/slo",
            idpMetadata = idpMetadata,
            requireSignedLogoutRequest = false,
            requireSignedLogoutResponse = false,
            requireDestination = false,
            signatureVerifier = SamlSignatureVerifier(idpMetadata),
            clockSkew = 60.seconds,
            replayCache = InMemorySamlReplayCache()
        )

        val oldIssueInstant = Clock.System.now() - 10.minutes
        val oldRequest = SamlTestUtils.createLogoutRequest(
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com",
            issueInstant = oldIssueInstant
        )
        assertFailsWith<SamlValidationException> {
            processor.processRequest(
                samlRequestBase64 = SamlTestUtils.encodeForPost(oldRequest),
                binding = SamlBinding.HttpPost
            )
        }

        val futureIssueInstant = Clock.System.now() + 5.minutes
        val futureRequest = SamlTestUtils.createLogoutRequest(
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com",
            issueInstant = futureIssueInstant
        )
        assertFailsWith<SamlValidationException> {
            processor.processRequest(
                samlRequestBase64 = SamlTestUtils.encodeForPost(futureRequest),
                binding = SamlBinding.HttpPost
            )
        }

        val withinSkew = Clock.System.now() + 30.seconds
        val validRequest = SamlTestUtils.createLogoutRequest(
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com",
            issueInstant = withinSkew
        )
        val result = processor.processRequest(
            samlRequestBase64 = SamlTestUtils.encodeForPost(validRequest),
            binding = SamlBinding.HttpPost
        )
        assertEquals("user@example.com", result.nameId)
    }

    @Test
    fun `IssueInstant validation for LogoutResponse`() {
        val idpMetadata = SamlTestUtils.createTestIdPMetadata()
        val processor = SamlLogoutProcessor(
            sloUrl = "https://sp.example.com/saml/slo",
            idpMetadata = idpMetadata,
            requireSignedLogoutRequest = false,
            requireSignedLogoutResponse = false,
            requireDestination = false,
            signatureVerifier = SamlSignatureVerifier(idpMetadata),
            clockSkew = 60.seconds,
            replayCache = InMemorySamlReplayCache()
        )

        val oldIssueInstant = Clock.System.now() - 10.minutes
        val oldResponse = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_request123",
            statusCode = StatusCode.SUCCESS,
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo",
            issueInstant = oldIssueInstant
        )
        assertFailsWith<SamlValidationException> {
            processor.processResponse(
                samlResponseBase64 = SamlTestUtils.encodeForPost(oldResponse),
                expectedRequestId = "_request123",
                binding = SamlBinding.HttpPost
            )
        }

        val futureIssueInstant = Clock.System.now() + 5.minutes
        val futureResponse = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_request123",
            statusCode = StatusCode.SUCCESS,
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo",
            issueInstant = futureIssueInstant
        )
        assertFailsWith<SamlValidationException> {
            processor.processResponse(
                samlResponseBase64 = SamlTestUtils.encodeForPost(futureResponse),
                expectedRequestId = "_request123",
                binding = SamlBinding.HttpPost
            )
        }
    }

    @Test
    fun `replay protection for LogoutRequest`() = runBlocking {
        val idpMetadata = SamlTestUtils.createTestIdPMetadata()
        val replayCache = InMemorySamlReplayCache()
        val processor = SamlLogoutProcessor(
            sloUrl = "https://sp.example.com/saml/slo",
            idpMetadata = idpMetadata,
            requireSignedLogoutRequest = false,
            requireSignedLogoutResponse = false,
            requireDestination = false,
            signatureVerifier = SamlSignatureVerifier(idpMetadata),
            clockSkew = 60.seconds,
            replayCache = replayCache
        )

        val firstRequest = SamlTestUtils.createLogoutRequest(
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com",
            requestId = "_fixed_request_id"
        )
        val encodedRequest = SamlTestUtils.encodeForPost(firstRequest)
        val result = processor.processRequest(
            samlRequestBase64 = encodedRequest,
            binding = SamlBinding.HttpPost
        )
        assertEquals("user@example.com", result.nameId)

        assertFailsWith<SamlValidationException> {
            processor.processRequest(samlRequestBase64 = encodedRequest, binding = SamlBinding.HttpPost)
        }

        val secondRequest = SamlTestUtils.createLogoutRequest(
            issuer = idpMetadata.entityId,
            destination = "https://sp.example.com/saml/slo",
            nameId = "user@example.com",
            requestId = "_different_request_id"
        )
        val result2 = processor.processRequest(
            samlRequestBase64 = SamlTestUtils.encodeForPost(secondRequest),
            binding = SamlBinding.HttpPost
        )
        assertEquals("user@example.com", result2.nameId)

        replayCache.close()
    }

    private fun createProcessor(
        idpMetadata: IdPMetadata,
        requireSignedLogoutRequest: Boolean = false,
        requireSignedLogoutResponse: Boolean = false,
        requireDestination: Boolean = false,
        clockSkew: kotlin.time.Duration = 60.seconds
    ) = SamlLogoutProcessor(
        sloUrl = "https://sp.example.com/saml/slo",
        idpMetadata = idpMetadata,
        requireSignedLogoutRequest = requireSignedLogoutRequest,
        requireSignedLogoutResponse = requireSignedLogoutResponse,
        requireDestination = requireDestination,
        signatureVerifier = SamlSignatureVerifier(idpMetadata),
        clockSkew = clockSkew,
        replayCache = InMemorySamlReplayCache()
    )

    companion object {
        private val TEST_CERTIFICATE_BASE64: String by lazy {
            val keyStore = buildKeyStore {
                certificate("test") {
                    password = "test"
                }
            }
            val cert = keyStore.getCertificate("test") as java.security.cert.X509Certificate
            Base64.encode(cert.encoded)
        }
    }
}
