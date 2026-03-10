/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Integration tests for SAML authentication.
 */
@OptIn(ExperimentalTime::class)
class SamlAuthTest {

    private fun ApplicationTestBuilder.noRedirectsClient() = createClient { followRedirects = false }

    @Test
    fun `test unauthenticated request redirects to IdP`() = testApplication {
        configureSamlAuth()

        val response = noRedirectsClient().get("/protected")

        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location]
        assertNotNull(location)
        assertTrue(location.startsWith(IDP_SSO_URL), "Should redirect to IdP SSO URL")

        // Verify AuthnRequest parameters in the redirect URL
        val url = Url(location)
        assertNotNull(url.parameters["SAMLRequest"], "SAMLRequest parameter should be present")
        assertEquals("/protected", url.parameters["RelayState"], "RelayState should contain original URL")
    }

    @Test
    fun `test redirect contains correct RelayState`() = testApplication {
        configureSamlAuth()

        val response = noRedirectsClient().get("/protected/resource?param=value")

        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location]
        assertNotNull(location)

        val url = Url(location)
        assertEquals("/protected/resource?param=value", url.parameters["RelayState"])
    }

    @Test
    fun `test unauthenticated request with HTTP-POST binding returns auto-submit form`() = testApplication {
        configureSamlAuth(authnRequestBinding = SamlBinding.HttpPost)

        val response = client.get("/protected")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), response.contentType())

        val body = response.bodyAsText()
        assertTrue(body.contains("<form method=\"POST\""), "Should contain POST form")
        assertTrue(
            body.contains("action=\"${IDP_SSO_URL.replace("&", "&amp;")}\""),
            "Form action should be IdP SSO URL"
        )
        assertTrue(body.contains("name=\"SAMLRequest\""), "Should contain SAMLRequest field")
        assertTrue(body.contains("name=\"RelayState\""), "Should contain RelayState field")
        assertTrue(body.contains("value=\"/protected\""), "RelayState should contain original URL")
        assertTrue(body.contains("onload=\"document.forms[0].submit()\""), "Should auto-submit")
    }

    @Test
    fun `test HTTP-POST binding sets session cookie`() = testApplication {
        configureSamlAuth(authnRequestBinding = SamlBinding.HttpPost)

        val response = client.get("/protected")

        assertEquals(HttpStatusCode.OK, response.status)
        val sessionCookie = response.headers[HttpHeaders.SetCookie]
        assertNotNull(sessionCookie, "SAML session cookie should be set")
    }

    @Test
    fun `test HTTP-POST binding contains RelayState with query parameters`() = testApplication {
        configureSamlAuth(authnRequestBinding = SamlBinding.HttpPost)

        val response = client.get("/protected/resource?param=value&other=123")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(
            body.contains("name=\"RelayState\" value=\"/protected/resource?param=value&amp;other=123\""),
            "RelayState should contain original URL with query parameters (HTML-escaped)"
        )
    }

    @Test
    fun `test default binding is HTTP-Redirect`() = testApplication {
        configureSamlAuth() // No explicit binding set

        val response = noRedirectsClient().get("/protected")

        assertEquals(HttpStatusCode.Found, response.status, "Default should use HTTP-Redirect")
        assertNotNull(response.headers[HttpHeaders.Location])
    }

    @Test
    fun `test successful authentication with signed assertion`() = testApplication {
        configureSamlAuth(wantAssertionsSigned = true)

        val assertion = SamlTestUtils.createTestAssertion(
            nameId = "user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        SamlTestUtils.signAssertion(assertion, idpCredentials.credential)

        val samlResponse = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(samlResponse)

        val response = client.post(ACS_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${base64Response.encodeURLParameter()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, user@example.com", response.bodyAsText())
    }

    @Test
    fun `test successful authentication with encrypted assertion`() = testApplication {
        configureSamlAuth(wantAssertionsSigned = false)

        val assertion = SamlTestUtils.createTestAssertion(
            nameId = "encrypted-user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val encryptedAssertion = SamlTestUtils.encryptAssertion(assertion, spCredentials.credential)
        val samlResponse = SamlTestUtils.createTestResponseWithEncryptedAssertion(
            encryptedAssertion = encryptedAssertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(samlResponse)

        val response = client.post(ACS_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${base64Response.encodeURLParameter()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, encrypted-user@example.com", response.bodyAsText())
    }

    @Test
    fun `test successful authentication with signed and encrypted assertion`() = testApplication {
        configureSamlAuth(wantAssertionsSigned = true)

        val assertion = SamlTestUtils.createTestAssertion(
            nameId = "secure-user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        SamlTestUtils.signAssertion(assertion, idpCredentials.credential)
        val encryptedAssertion = SamlTestUtils.encryptAssertion(assertion, spCredentials.credential)

        val samlResponse = SamlTestUtils.createTestResponseWithEncryptedAssertion(
            encryptedAssertion = encryptedAssertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(samlResponse)

        val response = client.post(ACS_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${base64Response.encodeURLParameter()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, secure-user@example.com", response.bodyAsText())
    }

    @Test
    fun `test RelayState redirect after successful authentication`() = testApplication {
        configureSamlAuth(wantAssertionsSigned = false)

        val assertion = SamlTestUtils.createTestAssertion(
            nameId = "user@example.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )

        val samlResponse = SamlTestUtils.createTestResponse(
            assertion = assertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val base64Response = SamlTestUtils.encodeResponseToBase64(samlResponse)

        val response = noRedirectsClient().post(ACS_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${base64Response.encodeURLParameter()}&RelayState=/original-page")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/original-page", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `test missing SAMLResponse parameter`() = testApplication {
        configureSamlAuth()

        val response = client.post(ACS_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("RelayState=/some-page")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `test custom challenge handler`() = testApplication {
        configureSamlAuth(
            wantAssertionsSigned = false,
            customChallenge = { cause ->
                call.respond(HttpStatusCode.Forbidden, "Custom challenge: $cause")
            }
        )

        // Missing SAMLResponse should trigger a challenge
        val response = client.post(ACS_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("RelayState=/some-page")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("Custom challenge"))
    }

    @Test
    fun `test validation function`() = testApplication {
        configureSamlAuth(
            wantAssertionsSigned = false,
            validateFunction = { credential ->
                // Only accept users from a specific domain
                val nameId = credential.assertion.subject?.nameID?.value
                if (nameId?.endsWith("@allowed.com") == true) {
                    SamlPrincipal(credential.assertion)
                } else {
                    null // Reject
                }
            }
        )

        val validAssertion = SamlTestUtils.createTestAssertion(
            nameId = "user@allowed.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val validSamlResponse = SamlTestUtils.createTestResponse(
            assertion = validAssertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val validBase64 = SamlTestUtils.encodeResponseToBase64(validSamlResponse)
        val validResponse = client.post(ACS_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${validBase64.encodeURLParameter()}")
        }

        assertEquals(HttpStatusCode.OK, validResponse.status)
        assertEquals("Hello, user@allowed.com", validResponse.bodyAsText())

        val invalidAssertion = SamlTestUtils.createTestAssertion(
            nameId = "user@notallowed.com",
            issuerEntityId = IDP_ENTITY_ID,
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = ACS_URL
        )
        val invalidSamlResponse = SamlTestUtils.createTestResponse(
            assertion = invalidAssertion,
            issuerEntityId = IDP_ENTITY_ID
        )
        val invalidBase64 = SamlTestUtils.encodeResponseToBase64(invalidSamlResponse)

        val response = client.post(ACS_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${invalidBase64.encodeURLParameter()}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `test SP-initiated authentication flow redirect`() = testApplication {
        configureSamlAuth(wantAssertionsSigned = false, allowIdpInitiatedSso = false)

        // Trigger SP-initiated flow
        val challengeResponse = noRedirectsClient().get("/protected")
        assertEquals(HttpStatusCode.Found, challengeResponse.status)

        // Verify the session cookie is set
        val sessionCookie = challengeResponse.headers[HttpHeaders.SetCookie]
        assertNotNull(sessionCookie, "SAML session cookie should be set")

        // Verify redirect to IdP with SAMLRequest
        val redirectUrl = Url(challengeResponse.headers[HttpHeaders.Location]!!)
        assertTrue(redirectUrl.toString().startsWith(IDP_SSO_URL), "Should redirect to IdP SSO URL")
        assertNotNull(redirectUrl.parameters["SAMLRequest"], "SAMLRequest should be in redirect URL")
    }

    @Test
    fun `test multiple SAML providers`() = testApplication {
        // Reuse shared credentials for IDP1, generate a unique one for IDP2 to test isolation
        val idp1Credentials = idpCredentials
        val idp2Credentials = SamlTestUtils.generateTestCredentials()

        val idp1Metadata = IdPMetadata {
            entityId = "https://idp1.example.com"
            ssoUrl = "https://idp1.example.com/sso"
            sloUrl = null
            signingCredentials = listOf(idp1Credentials.credential)
        }

        val idp2Metadata = IdPMetadata {
            entityId = "https://idp2.example.com"
            ssoUrl = "https://idp2.example.com/sso"
            sloUrl = null
            signingCredentials = listOf(idp2Credentials.credential)
        }

        // Sessions plugin is required for SAML auth to store the request ID
        install(Sessions) {
            cookie<SamlSession>("SAML_SESSION")
        }

        install(Authentication) {
            saml("idp1") {
                sp = SamlSpMetadata {
                    spEntityId = SP_ENTITY_ID
                    acsUrl = "http://localhost/saml/acs/idp1"
                    wantAssertionsSigned = false
                }
                idp = idp1Metadata
                allowIdpInitiatedSso = true
                requireDestination = false
                validate { credential ->
                    SamlPrincipal(credential.assertion)
                }
            }
            saml("idp2") {
                sp = SamlSpMetadata {
                    spEntityId = SP_ENTITY_ID
                    acsUrl = "http://localhost/saml/acs/idp2"
                    wantAssertionsSigned = false
                }
                idp = idp2Metadata
                allowIdpInitiatedSso = true
                requireDestination = false
                validate { credential ->
                    SamlPrincipal(credential.assertion)
                }
            }
        }

        routing {
            authenticate("idp1") {
                get("/protected/idp1") {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("IDP1: ${principal.nameId}")
                }
                post("/saml/acs/idp1") {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("IDP1: ${principal.nameId}")
                }
            }
            authenticate("idp2") {
                get("/protected/idp2") {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("IDP2: ${principal.nameId}")
                }
                post("/saml/acs/idp2") {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("IDP2: ${principal.nameId}")
                }
            }
        }

        // Test IDP1
        val assertion1 = SamlTestUtils.createTestAssertion(
            nameId = "user1@idp1.com",
            issuerEntityId = "https://idp1.example.com",
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = "http://localhost/saml/acs/idp1"
        )
        val samlResponse1 = SamlTestUtils.createTestResponse(
            assertion = assertion1,
            issuerEntityId = "https://idp1.example.com"
        )
        val base64Response1 = SamlTestUtils.encodeResponseToBase64(samlResponse1)

        val response1 = client.post("/saml/acs/idp1") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${base64Response1.encodeURLParameter()}")
        }
        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals("IDP1: user1@idp1.com", response1.bodyAsText())

        // Test IDP2
        val assertion2 = SamlTestUtils.createTestAssertion(
            nameId = "user2@idp2.com",
            issuerEntityId = "https://idp2.example.com",
            audienceEntityId = SP_ENTITY_ID,
            recipientUrl = "http://localhost/saml/acs/idp2"
        )
        val samlResponse2 = SamlTestUtils.createTestResponse(
            assertion = assertion2,
            issuerEntityId = "https://idp2.example.com"
        )
        val base64Response2 = SamlTestUtils.encodeResponseToBase64(samlResponse2)

        val response2 = client.post("/saml/acs/idp2") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${base64Response2.encodeURLParameter()}")
        }
        assertEquals(HttpStatusCode.OK, response2.status)
        assertEquals("IDP2: user2@idp2.com", response2.bodyAsText())
    }

    private fun ApplicationTestBuilder.configureSamlAuth(
        wantAssertionsSigned: Boolean = false,
        useKeyStore: Boolean = true,
        allowIdpInitiatedSso: Boolean = true,
        requireDestination: Boolean = false,
        authnRequestBinding: SamlBinding = SamlBinding.HttpRedirect,
        customChallenge: (suspend SamlChallengeContext.(AuthenticationFailedCause) -> Unit)? = null,
        validateFunction: (suspend io.ktor.server.application.ApplicationCall.(SamlCredential) -> Any?)? = null
    ) {
        install(Sessions) {
            cookie<SamlSession>("SAML_SESSION")
        }

        install(Authentication) {
            saml("saml-auth") {
                sp = SamlSpMetadata {
                    spEntityId = SP_ENTITY_ID
                    acsUrl = ACS_URL
                    this.wantAssertionsSigned = wantAssertionsSigned

                    if (useKeyStore) {
                        signingCredential = SamlCrypto.loadCredential(
                            keystorePath = spKeyStoreFile.absolutePath,
                            keystorePassword = "test-pass",
                            keyAlias = "sp-key",
                            keyPassword = "test-pass"
                        )
                    }
                }
                idp = IdPMetadata {
                    entityId = IDP_ENTITY_ID
                    ssoUrl = IDP_SSO_URL
                    sloUrl = null
                    signingCredentials = listOf(idpCredentials.credential)
                }
                this.allowIdpInitiatedSso = allowIdpInitiatedSso
                this.requireDestination = requireDestination
                this.authnRequestBinding = authnRequestBinding

                validate(
                    validateFunction ?: { credential ->
                        SamlPrincipal(credential.assertion)
                    }
                )

                if (customChallenge != null) {
                    challenge(customChallenge)
                }
            }
        }

        routing {
            authenticate("saml-auth") {
                get("/protected") {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("Hello, ${principal.nameId}")
                }
                get("/protected/{path...}") {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("Hello, ${principal.nameId}")
                }
                post(ACS_PATH) {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("Hello, ${principal.nameId}")
                }
            }
        }
    }

    companion object {
        private const val SP_ENTITY_ID = "https://sp.example.com"
        private const val IDP_ENTITY_ID = "https://idp.example.com"
        private const val ACS_PATH = "/saml/acs"
        private const val ACS_URL = "http://localhost$ACS_PATH"
        private const val IDP_SSO_URL = "https://idp.example.com/sso"

        private val idpCredentials: SamlTestUtils.TestCredentials by lazy {
            SamlTestUtils.sharedIdpCredentials
        }
        private val spCredentials: SamlTestUtils.TestCredentials by lazy {
            SamlTestUtils.sharedSpCredentials
        }

        private val spKeyStoreFile: File by lazy {
            File.createTempFile("sp-keystore", ".jks").also { file ->
                file.deleteOnExit()
                spCredentials.saveToKeyStore(
                    file = file,
                    storePassword = "test-pass",
                    keyAlias = "sp-key",
                    keyPassword = "test-pass"
                )
            }
        }
    }
}
