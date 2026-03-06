/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import kotlinx.coroutines.test.runTest
import org.opensaml.saml.saml2.core.StatusCode
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SamlResponseProcessorTest {
    private lateinit var replayCache: InMemorySamlReplayCache

    @BeforeTest
    fun setup() {
        replayCache = InMemorySamlReplayCache()
    }

    @AfterTest
    fun teardown() {
        replayCache.close()
    }

    @Test
    fun `test decrypt encrypted assertion successfully`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)

        // Create assertion with audience for SP
        val assertion = SamlTestUtils.createTestAssertion(
            nameId = "user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val encryptedAssertion = SamlTestUtils.encryptAssertion(assertion, spCredentials.credential)
        val response = SamlTestUtils.createTestResponseWithEncryptedAssertion(
            encryptedAssertion = encryptedAssertion,
            issuerEntityId = IDP_ENTITY_ID
        )

        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        val credential = processor.processResponse(base64Response, null)
        assertEquals("user@example.com", credential.assertion.subject?.nameID?.value)
    }

    @Test
    fun `test decrypt fails without decryption credential`() = runTest {
        val idpMetadata = IdPMetadata(
            entityId = IDP_ENTITY_ID,
            ssoUrl = "https://idp.example.com/sso",
            sloUrl = null,
            signingCredentials = listOf(idpCredentials.credential)
        )
        val processor = SamlResponseProcessor(
            spEntityId = SP_ENTITY_ID,
            acsUrl = ACS_URL,
            idpMetadata = idpMetadata,
            decryptionCredential = null, // No decryption credential
            clockSkew = 5.minutes,
            replayCache = replayCache,
            requireSignedAssertions = false,
            requireSignedResponse = false,
            requireDestination = false,
            allowIdpInitiatedSso = true,
            signatureVerifier = SamlSignatureVerifier(idpMetadata)
        )

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID
        )
        val encryptedAssertion = SamlTestUtils.encryptAssertion(assertion, spCredentials.credential)
        val response = SamlTestUtils.createTestResponseWithEncryptedAssertion(
            encryptedAssertion = encryptedAssertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, null)
        }
    }

    @Test
    fun `test decrypt fails with wrong key`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)
        val otherCredentials = SamlTestUtils.generateTestCredentials()
        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID
        )
        val encryptedAssertion = SamlTestUtils.encryptAssertion(assertion, otherCredentials.credential)
        val response = SamlTestUtils.createTestResponseWithEncryptedAssertion(
            encryptedAssertion = encryptedAssertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, expectedRequestId = null)
        }
    }

    @Test
    fun `test response signature validation`() = runTest {
        val processorRequiresSig = createProcessor(requireSignedAssertions = false, requireSignedResponse = true)
        val processorNoSigRequired = createProcessor(requireSignedAssertions = false, requireSignedResponse = false)

        // Valid signed response is accepted
        val assertion1 = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val response1 = SamlTestUtils.createTestResponse(assertion = assertion1, issuerEntityId = IDP_ENTITY_ID)
        SamlTestUtils.signResponse(response1, idpCredentials.credential)
        assertNotNull(processorRequiresSig.processResponse(SamlTestUtils.encodeResponseToBase64(response1), null))

        // Unsigned response is rejected when signature required
        val assertion2 = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val response2 = SamlTestUtils.createTestResponse(assertion = assertion2, issuerEntityId = IDP_ENTITY_ID)
        assertFailsWith<SamlValidationException> {
            processorRequiresSig.processResponse(SamlTestUtils.encodeResponseToBase64(response2), null)
        }

        // Test 3: Unsigned response is accepted when signature not required
        val assertion3 = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val response3 = SamlTestUtils.createTestResponse(assertion = assertion3, issuerEntityId = IDP_ENTITY_ID)
        assertNotNull(processorNoSigRequired.processResponse(SamlTestUtils.encodeResponseToBase64(response3), null))
    }

    @Test
    fun `test response with invalid signature is rejected`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false, requireSignedResponse = true)

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val response = SamlTestUtils.createTestResponse(assertion = assertion, issuerEntityId = IDP_ENTITY_ID)

        val wrongCredentials = SamlTestUtils.generateTestCredentials()
        SamlTestUtils.signResponse(response, wrongCredentials.credential)

        assertFailsWith<SamlValidationException> {
            processor.processResponse(SamlTestUtils.encodeResponseToBase64(response), null)
        }
    }

    @Test
    fun `test assertion signature validation`() = runTest {
        val processorRequiresSig = createProcessor(requireSignedAssertions = true)
        val processorNoSigRequired = createProcessor(requireSignedAssertions = false)

        // Valid signed assertion is accepted
        val assertion1 = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        SamlTestUtils.signAssertion(assertion1, idpCredentials.credential)
        val response1 = SamlTestUtils.createTestResponse(assertion = assertion1, issuerEntityId = IDP_ENTITY_ID)
        assertNotNull(processorRequiresSig.processResponse(SamlTestUtils.encodeResponseToBase64(response1), null))

        // Unsigned assertion is rejected when signature required
        val assertion2 = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID
        )
        val response2 = SamlTestUtils.createTestResponse(assertion = assertion2, issuerEntityId = IDP_ENTITY_ID)
        assertFailsWith<SamlValidationException> {
            processorRequiresSig.processResponse(SamlTestUtils.encodeResponseToBase64(response2), null)
        }

        // Unsigned assertion is accepted when signature not required
        val assertion3 = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val response3 = SamlTestUtils.createTestResponse(assertion = assertion3, issuerEntityId = IDP_ENTITY_ID)
        assertNotNull(processorNoSigRequired.processResponse(SamlTestUtils.encodeResponseToBase64(response3), null))
    }

    @Test
    fun `test assertion with invalid signature is rejected`() = runTest {
        val processor = createProcessor(requireSignedAssertions = true)

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID
        )

        val wrongCredentials = SamlTestUtils.generateTestCredentials()
        SamlTestUtils.signAssertion(assertion, wrongCredentials.credential)

        val response = SamlTestUtils.createTestResponse(assertion = assertion, issuerEntityId = IDP_ENTITY_ID)
        assertFailsWith<SamlValidationException> {
            processor.processResponse(SamlTestUtils.encodeResponseToBase64(response), null)
        }
    }

    @Test
    fun `test reject wrong audience`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = "https://wrong-sp.example.com", // Wrong audience!
            recipientUrl = ACS_URL
        )

        val response = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, null)
        }
    }

    @Test
    fun `test reject expired assertion`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false, clockSkew = 1.minutes)

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            notBefore = Clock.System.now() - 1.hours,
            notOnOrAfter = Clock.System.now() - 10.minutes
        )

        val response = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)
        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, null)
        }
    }

    @Test
    fun `test reject assertion not yet valid`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false, clockSkew = 1.minutes)

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            notBefore = Clock.System.now() + 10.minutes,
            notOnOrAfter = Clock.System.now() + 20.minutes
        )

        val response = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, null)
        }
    }

    @Test
    fun `test reject response with wrong issuer`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID
        )
        val response = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = "https://wrong-idp.example.com" // Wrong issuer!
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, null)
        }
    }

    @Test
    fun `test reject response with InResponseTo mismatch`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )

        val response = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID,
            inResponseTo = "_wrong-request-id"
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, "_expected-request-id")
        }
    }

    @Test
    fun `test reject error response status`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)
        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID
        )
        val response = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID,
            statusCode = StatusCode.RESPONDER // Error status
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)
        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, null)
        }
    }

    @Test
    fun `test IdP-initiated SSO handling`() = runTest {
        val processorAllowIdpInit = createProcessor(requireSignedAssertions = false, allowIdpInitiatedSso = true)
        val processorDisallowIdpInit = createProcessor(requireSignedAssertions = false, allowIdpInitiatedSso = false)

        // IdP-initiated SSO is accepted when allowed
        val assertion1 = SamlTestUtils.createTestAssertion(
            nameId = "idp-initiated-user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val response1 = SamlTestUtils.createTestResponse(
            assertion = assertion1,
            issuerEntityId = IDP_ENTITY_ID,
            inResponseTo = null
        )
        val credential1 = processorAllowIdpInit.processResponse(
            SamlTestUtils.encodeResponseToBase64(response1),
            expectedRequestId = null
        )
        assertEquals("idp-initiated-user@example.com", credential1.assertion.subject?.nameID?.value)

        // IdP-initiated SSO is rejected when not allowed
        val assertion2 = SamlTestUtils.createTestAssertion(
            nameId = "idp-initiated-user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val response2 = SamlTestUtils.createTestResponse(
            assertion = assertion2,
            issuerEntityId = IDP_ENTITY_ID,
            inResponseTo = null
        )
        val exception = assertFailsWith<SamlValidationException> {
            processorDisallowIdpInit.processResponse(
                SamlTestUtils.encodeResponseToBase64(response2),
                expectedRequestId = null
            )
        }
        assertTrue(exception.message!!.contains("IdP-initiated SSO is not allowed"))

        // SP-initiated flow works when IdP-initiated is disabled
        val requestId = "_sp-initiated-request-123"
        val assertion3 = SamlTestUtils.createTestAssertion(
            nameId = "sp-initiated-user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL,
            inResponseTo = requestId
        )
        val response3 = SamlTestUtils.createTestResponse(
            assertion = assertion3,
            issuerEntityId = IDP_ENTITY_ID,
            inResponseTo = requestId
        )
        val credential3 = processorDisallowIdpInit.processResponse(
            SamlTestUtils.encodeResponseToBase64(response3),
            expectedRequestId = requestId
        )
        assertEquals("sp-initiated-user@example.com", credential3.assertion.subject?.nameID?.value)

        // InResponseTo is validated even when IdP-initiated is enabled
        val wrongRequestId = "_wrong-response-id"
        val assertion4 = SamlTestUtils.createTestAssertion(
            nameId = "user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL,
            inResponseTo = wrongRequestId
        )
        val response4 = SamlTestUtils.createTestResponse(
            assertion = assertion4,
            issuerEntityId = IDP_ENTITY_ID,
            inResponseTo = wrongRequestId
        )
        assertFailsWith<SamlValidationException> {
            processorAllowIdpInit.processResponse(
                SamlTestUtils.encodeResponseToBase64(response4),
                expectedRequestId = "_expected-request-id"
            )
        }
    }

    @Test
    fun `test reject replayed assertion`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)

        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )

        val response = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        val credential = processor.processResponse(base64Response, null)
        assertNotNull(credential)

        // The second request with the same assertion should fail (replay)
        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, null)
        }
    }

    @Test
    fun `test process complete signed and encrypted response`() = runTest {
        val processor = createProcessor(requireSignedAssertions = true)

        val requestId = "_test-request-123"
        val assertion = SamlTestUtils.createTestAssertion(
            nameId = "john.doe@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL,
            inResponseTo = requestId
        )

        SamlTestUtils.signAssertion(assertion, idpCredentials.credential)
        val encryptedAssertion = SamlTestUtils.encryptAssertion(assertion, spCredentials.credential)
        val response = SamlTestUtils.createTestResponseWithEncryptedAssertion(
            encryptedAssertion = encryptedAssertion,
            issuerEntityId = IDP_ENTITY_ID,
            inResponseTo = requestId
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)

        val credential = processor.processResponse(base64Response, requestId)

        assertEquals("john.doe@example.com", credential.assertion.subject?.nameID?.value)
        assertEquals(IDP_ENTITY_ID, credential.assertion.issuer?.value)
    }

    @Test
    fun `test assertion issuer mismatch`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)
        val assertion = SamlTestUtils.createTestAssertion(
            issuerEntityId = "https://wrong-idp.example.com", // Wrong issuer in assertion!
            audienceEntityId = SP_ENTITY_ID
        )
        val response = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID // Correct issuer in response
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(response)
        assertFailsWith<SamlValidationException> {
            processor.processResponse(base64Response, null)
        }
    }

    @Test
    fun `test Destination validation`() = runTest {
        val processorDefault = createProcessor(requireSignedAssertions = false)
        val assertionWrongDest = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val responseWrongDest = SamlTestUtils.createTestResponse(
            assertion = assertionWrongDest,
            issuerEntityId = IDP_ENTITY_ID,
            destination = "https://attacker.example.com/acs"
        )
        assertFailsWith<SamlValidationException> {
            processorDefault.processResponse(SamlTestUtils.encodeResponseToBase64(responseWrongDest), null)
        }

        val processorNoDestRequired = createProcessor(requireSignedAssertions = false, requireDestination = false)
        val assertionNoDest = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val responseNoDest = SamlTestUtils.createTestResponse(
            assertion = assertionNoDest,
            issuerEntityId = IDP_ENTITY_ID,
            destination = null
        )
        assertNotNull(
            processorNoDestRequired.processResponse(SamlTestUtils.encodeResponseToBase64(responseNoDest), null)
        )

        val processorDestRequired = createProcessor(requireSignedAssertions = false, requireDestination = true)
        val assertionNoDestRequired = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val responseNoDestRequired = SamlTestUtils.createTestResponse(
            assertion = assertionNoDestRequired,
            issuerEntityId = IDP_ENTITY_ID,
            destination = null
        )
        val exception = assertFailsWith<SamlValidationException> {
            processorDestRequired.processResponse(SamlTestUtils.encodeResponseToBase64(responseNoDestRequired), null)
        }
        assertTrue(exception.message!!.contains("Destination"))

        val assertionCorrectDest = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val responseCorrectDest = SamlTestUtils.createTestResponse(
            assertion = assertionCorrectDest,
            issuerEntityId = IDP_ENTITY_ID,
            destination = ACS_URL
        )
        assertNotNull(processorDefault.processResponse(SamlTestUtils.encodeResponseToBase64(responseCorrectDest), null))
    }

    @Test
    fun `test Recipient validation`() = runTest {
        val processor = createProcessor(requireSignedAssertions = false)

        // Wrong recipient is rejected
        val assertionWrongRecipient = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = "https://attacker.example.com/acs"
        )
        val responseWrongRecipient = SamlTestUtils.createTestResponse(
            assertion = assertionWrongRecipient,
            issuerEntityId = IDP_ENTITY_ID
        )
        assertFailsWith<SamlValidationException> {
            processor.processResponse(SamlTestUtils.encodeResponseToBase64(responseWrongRecipient), null)
        }

        // Missing recipient is accepted
        val assertionNoRecipient = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = null
        )
        val responseNoRecipient = SamlTestUtils.createTestResponse(
            assertion = assertionNoRecipient,
            issuerEntityId = IDP_ENTITY_ID
        )
        assertNotNull(processor.processResponse(SamlTestUtils.encodeResponseToBase64(responseNoRecipient), null))

        // The correct recipient is accepted
        val assertionCorrectRecipient = SamlTestUtils.createTestAssertion(
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val responseCorrectRecipient = SamlTestUtils.createTestResponse(
            assertion = assertionCorrectRecipient,
            issuerEntityId = IDP_ENTITY_ID
        )
        assertNotNull(processor.processResponse(SamlTestUtils.encodeResponseToBase64(responseCorrectRecipient), null))
    }

    private fun createProcessor(
        requireSignedAssertions: Boolean = true,
        requireSignedResponse: Boolean = false,
        requireDestination: Boolean = false,
        clockSkew: Duration = 5.minutes,
        acsUrl: String = ACS_URL,
        allowIdpInitiatedSso: Boolean = true
    ): SamlResponseProcessor {
        val idpMetadata = IdPMetadata(
            entityId = IDP_ENTITY_ID,
            ssoUrl = "https://idp.example.com/sso",
            sloUrl = null,
            signingCredentials = listOf(idpCredentials.credential)
        )
        return SamlResponseProcessor(
            spEntityId = SP_ENTITY_ID,
            acsUrl = acsUrl,
            idpMetadata = idpMetadata,
            decryptionCredential = spCredentials.credential,
            clockSkew = clockSkew,
            replayCache = replayCache,
            requireSignedAssertions = requireSignedAssertions,
            requireSignedResponse = requireSignedResponse,
            requireDestination = requireDestination,
            allowIdpInitiatedSso = allowIdpInitiatedSso,
            signatureVerifier = SamlSignatureVerifier(idpMetadata)
        )
    }

    companion object {
        private const val SP_ENTITY_ID = "https://sp.example.com"
        private const val IDP_ENTITY_ID = "https://idp.example.com"
        private const val ACS_URL = "https://sp.example.com/saml/acs"
        private val idpCredentials: SamlTestUtils.TestCredentials by lazy {
            SamlTestUtils.sharedIdpCredentials
        }
        private val spCredentials: SamlTestUtils.TestCredentials by lazy {
            SamlTestUtils.sharedSpCredentials
        }
    }
}
