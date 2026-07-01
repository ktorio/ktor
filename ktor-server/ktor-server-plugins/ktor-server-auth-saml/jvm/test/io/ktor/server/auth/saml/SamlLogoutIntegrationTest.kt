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
import org.opensaml.saml.saml2.core.StatusCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Integration tests for SAML Single Logout functionality
 */
@OptIn(ExperimentalTime::class)
class SamlLogoutIntegrationTest {

    private fun ApplicationTestBuilder.noRedirectsClient() = createClient { followRedirects = false }

    @Test
    fun `test samlLogout builds redirect with RelayState and updates session`() = testApplication {
        configureSamlAuth(enableSingleLogout = true)

        val basicResponse = noRedirectsClient().get("/test-logout")
        assertEquals(HttpStatusCode.Found, basicResponse.status)

        val location = basicResponse.headers[HttpHeaders.Location]
        assertNotNull(location, "Should redirect to IdP SLO URL")
        assertTrue(location.startsWith(IDP_SLO_URL), "Should redirect to IdP SLO URL")
        assertTrue(location.contains("SAMLRequest="), "Redirect URL should contain SAMLRequest")

        val sessionCookie = basicResponse.headers[HttpHeaders.SetCookie]
        assertNotNull(sessionCookie, "Session should be updated with logoutRequestId")

        // Test logout with RelayState
        val relayResponse = noRedirectsClient().get("/test-logout-with-relay")
        assertEquals(HttpStatusCode.Found, relayResponse.status)

        val relayLocation = relayResponse.headers[HttpHeaders.Location]
        assertNotNull(relayLocation)
        assertTrue(relayLocation.contains("RelayState="), "Redirect URL should contain RelayState")
    }

    @Test
    fun `test IdP-initiated logout via HTTP-POST with RelayState`() = testApplication {
        configureSamlAuth(enableSingleLogout = true)

        // First request without RelayState
        val logoutRequestXml1 = SamlTestUtils.createLogoutRequest(
            issuer = IDP_ENTITY_ID,
            destination = SLO_URL,
            nameId = "user@example.com",
            sessionIndex = "_session123",
            requestId = "_request_1"
        )
        val base64Request1 = SamlTestUtils.encodeForPost(logoutRequestXml1)

        val response = noRedirectsClient().post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLRequest=${base64Request1.encodeURLParameter()}")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location]
        assertNotNull(location, "Should redirect to IdP with LogoutResponse")
        assertTrue(location.startsWith(IDP_SLO_URL), "Should redirect to IdP SLO URL")
        assertTrue(location.contains("SAMLResponse="), "Redirect should contain SAMLResponse")

        // Second request with RelayState (using a different request ID to avoid replay detection)
        val logoutRequestXml2 = SamlTestUtils.createLogoutRequest(
            issuer = IDP_ENTITY_ID,
            destination = SLO_URL,
            nameId = "user@example.com",
            sessionIndex = "_session123",
            requestId = "_request_2"
        )
        val base64Request2 = SamlTestUtils.encodeForPost(logoutRequestXml2)

        val relayResponse = noRedirectsClient().post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLRequest=${base64Request2.encodeURLParameter()}&RelayState=/post-logout")
        }

        assertEquals(HttpStatusCode.Found, relayResponse.status)
        val relayLocation = relayResponse.headers[HttpHeaders.Location]
        assertNotNull(relayLocation)
        assertTrue(relayLocation.contains("RelayState="), "RelayState should be preserved")
    }

    @Test
    fun `test IdP-initiated logout via HTTP-GET`() = testApplication {
        configureSamlAuth(enableSingleLogout = true)

        val logoutRequestXml = SamlTestUtils.createLogoutRequest(
            issuer = IDP_ENTITY_ID,
            destination = SLO_URL,
            nameId = "user@example.com"
        )
        val encodedRequest = logoutRequestXml.encodeSamlMessage(deflate = true)

        val response = noRedirectsClient().get(SLO_PATH) {
            parameter("SAMLRequest", encodedRequest)
        }

        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location]
        assertNotNull(location)
        assertTrue(location.contains("SAMLResponse="), "Should respond with SAMLResponse")
    }

    @Test
    fun `test LogoutResponse processing with success and failure status`() = testApplication {
        configureSamlAuth(enableSingleLogout = true)

        val testClient = noRedirectsClient()

        // Initiate SP-initiated logout to populate session with logoutRequestId
        val initiateResponse = testClient.get("/test-logout")
        assertEquals(HttpStatusCode.Found, initiateResponse.status)
        val sessionCookie = initiateResponse.headers[HttpHeaders.SetCookie]
        assertNotNull(sessionCookie, "Session cookie should be set")
        val logoutRequestId = initiateResponse.headers["X-Logout-Request-Id"]
        assertNotNull(logoutRequestId, "LogoutRequest ID should be returned in header")

        // Test SUCCESS case: InResponseTo matches the stored logoutRequestId
        val successResponseXml = SamlTestUtils.createLogoutResponse(
            inResponseTo = logoutRequestId,
            statusCode = StatusCode.SUCCESS,
            issuer = IDP_ENTITY_ID,
            destination = SLO_URL
        )
        val successBase64 = SamlTestUtils.encodeForPost(successResponseXml)

        val successResponse = testClient.post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            header(HttpHeaders.Cookie, sessionCookie.substringBefore(";"))
            setBody("SAMLResponse=${successBase64.encodeURLParameter()}")
        }

        assertEquals(HttpStatusCode.OK, successResponse.status)
        assertTrue(successResponse.bodyAsText().contains("Logout completed"))

        // Test FAILURE case: InResponseTo does NOT match the stored logoutRequestId
        // Re-initiate logout to get a fresh session with logoutRequestId
        val reinitiateResponse = testClient.get("/test-logout")
        assertEquals(HttpStatusCode.Found, reinitiateResponse.status)
        val freshSessionCookie = reinitiateResponse.headers[HttpHeaders.SetCookie]
        assertNotNull(freshSessionCookie)

        val mismatchedResponseXml = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_different_request_id",
            statusCode = StatusCode.SUCCESS,
            issuer = IDP_ENTITY_ID,
            destination = SLO_URL
        )
        val mismatchedBase64 = SamlTestUtils.encodeForPost(mismatchedResponseXml)

        val mismatchedResponse = testClient.post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            header(HttpHeaders.Cookie, freshSessionCookie.substringBefore(";"))
            setBody("SAMLResponse=${mismatchedBase64.encodeURLParameter()}")
        }

        // Mismatched InResponseTo should result in BadRequest (InResponseTo mismatch is caught as validation error)
        assertEquals(HttpStatusCode.BadRequest, mismatchedResponse.status)
        assertTrue(mismatchedResponse.bodyAsText().contains("Invalid logout response"))

        // Test IdP failure case: InResponseTo matches but IdP reports failure status
        val reinitiateResponse2 = testClient.get("/test-logout")
        assertEquals(HttpStatusCode.Found, reinitiateResponse2.status)
        val freshSessionCookie2 = reinitiateResponse2.headers[HttpHeaders.SetCookie]
        assertNotNull(freshSessionCookie2)
        val logoutRequestId2 = reinitiateResponse2.headers["X-Logout-Request-Id"]
        assertNotNull(logoutRequestId2)

        val failureResponseXml = SamlTestUtils.createLogoutResponse(
            inResponseTo = logoutRequestId2,
            statusCode = StatusCode.RESPONDER,
            statusMessage = "Logout failed at IdP",
            issuer = IDP_ENTITY_ID,
            destination = SLO_URL
        )
        val failureBase64 = SamlTestUtils.encodeForPost(failureResponseXml)

        val failureResponse = testClient.post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            header(HttpHeaders.Cookie, freshSessionCookie2.substringBefore(";"))
            setBody("SAMLResponse=${failureBase64.encodeURLParameter()}")
        }

        // Non-success LogoutResponse should result in BadGateway (IdP failed to complete logout)
        assertEquals(HttpStatusCode.BadGateway, failureResponse.status)
        assertTrue(failureResponse.bodyAsText().contains("IdP logout failed"))
    }

    @Test
    fun `test LogoutResponse with RelayState redirects`() = testApplication {
        configureSamlAuth(enableSingleLogout = true)

        val testClient = noRedirectsClient()

        // Initiate SP-initiated logout to populate session with logoutRequestId
        val initiateResponse = testClient.get("/test-logout")
        assertEquals(HttpStatusCode.Found, initiateResponse.status)
        val sessionCookie = initiateResponse.headers[HttpHeaders.SetCookie]
        assertNotNull(sessionCookie)
        val logoutRequestId = initiateResponse.headers["X-Logout-Request-Id"]
        assertNotNull(logoutRequestId)

        val logoutResponseXml = SamlTestUtils.createLogoutResponse(
            inResponseTo = logoutRequestId,
            statusCode = StatusCode.SUCCESS,
            issuer = IDP_ENTITY_ID,
            destination = SLO_URL
        )
        val base64Response = SamlTestUtils.encodeForPost(logoutResponseXml)

        val response = testClient.post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            header(HttpHeaders.Cookie, sessionCookie.substringBefore(";"))
            setBody("SAMLResponse=${base64Response.encodeURLParameter()}&RelayState=/post-logout-page")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location]
        assertEquals("/post-logout-page", location)
    }

    @Test
    fun `test SLO endpoint disabled when enableSingleLogout is false`() = testApplication {
        configureSamlAuth(enableSingleLogout = false)

        val logoutRequestXml = SamlTestUtils.createLogoutRequest(
            issuer = IDP_ENTITY_ID,
            destination = SLO_URL,
            nameId = "user@example.com"
        )
        val base64Request = SamlTestUtils.encodeForPost(logoutRequestXml)

        val response = noRedirectsClient().post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLRequest=${base64Request.encodeURLParameter()}")
        }

        // When SLO is disabled, the endpoint triggers an auth challenge (redirect to IdP for SSO)
        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location]
        assertNotNull(location)
        assertTrue(location.startsWith(IDP_SSO_URL), "Should redirect to IdP SSO URL when SLO is disabled")
    }

    @Test
    fun `test SLO rejects invalid requests`() = testApplication {
        configureSamlAuth(enableSingleLogout = true)

        // Test unsupported HTTP method
        val putResponse = client.put(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLRequest=test")
        }
        assertEquals(HttpStatusCode.MethodNotAllowed, putResponse.status)

        // Test missing SAMLRequest and SAMLResponse
        val missingResponse = client.post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("RelayState=/some-page")
        }
        assertEquals(HttpStatusCode.BadRequest, missingResponse.status)
    }

    @Test
    fun `test SLO rejects wrong issuer`() = testApplication {
        configureSamlAuth(enableSingleLogout = true)

        // Test LogoutRequest with the wrong issuer
        val badRequestXml = SamlTestUtils.createLogoutRequest(
            issuer = "https://malicious-idp.example.com",
            destination = SLO_URL,
            nameId = "user@example.com"
        )
        val badRequestBase64 = SamlTestUtils.encodeForPost(badRequestXml)

        val requestResponse = client.post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLRequest=${badRequestBase64.encodeURLParameter()}")
        }
        assertEquals(HttpStatusCode.BadRequest, requestResponse.status)

        // Test LogoutResponse with the wrong issuer
        val badResponseXml = SamlTestUtils.createLogoutResponse(
            inResponseTo = "_test_request",
            statusCode = StatusCode.SUCCESS,
            issuer = "https://malicious-idp.example.com",
            destination = SLO_URL
        )
        val badResponseBase64 = SamlTestUtils.encodeForPost(badResponseXml)

        val responseResponse = client.post(SLO_PATH) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("SAMLResponse=${badResponseBase64.encodeURLParameter()}")
        }
        assertEquals(HttpStatusCode.BadRequest, responseResponse.status)
    }

    private fun ApplicationTestBuilder.configureSamlAuth(enableSingleLogout: Boolean = false) {
        install(Sessions) {
            cookie<SamlSession>("SAML_SESSION")
        }

        val spMetadata = SamlSpMetadata {
            spEntityId = SP_ENTITY_ID
            acsUrl = ACS_URL
            sloUrl = SLO_URL
            wantAssertionsSigned = false

            if (enableSingleLogout) {
                signingCredential = SamlCrypto.loadCredential(
                    keystorePath = spKeyStoreFile.absolutePath,
                    keystorePassword = "test-pass",
                    keyAlias = "sp-key",
                    keyPassword = "test-pass"
                )
            }
        }

        val testIdpMetadata = SamlTestUtils.createTestIdPMetadataWithSlo(
            entityId = IDP_ENTITY_ID,
            ssoUrl = IDP_SSO_URL,
            sloUrl = IDP_SLO_URL
        )

        install(Authentication) {
            saml("saml-auth") {
                this.sp = spMetadata
                this.enableSingleLogout = enableSingleLogout
                this.idp = testIdpMetadata
                allowIdpInitiatedSso = true
                requireDestination = false
                requireSignedResponse = false
                requireSignedLogoutRequest = false
                validate { credential ->
                    SamlPrincipal(credential.assertion)
                }
            }
        }

        routing {
            authenticate("saml-auth") {
                get("/protected") {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("Hello, ${principal.nameId}")
                }
                post(ACS_PATH) {
                    val principal = call.principal<SamlPrincipal>()!!
                    call.respondText("Hello, ${principal.nameId}")
                }
                // SLO endpoint must be under an authenticated block for the provider to handle it
                get(SLO_PATH) {
                    call.respondText("SLO not handled")
                }
                post(SLO_PATH) {
                    call.respondText("SLO not handled")
                }
            }

            if (enableSingleLogout) {
                get("/test-logout") {
                    // Create a session for the test (normally this would be created during authentication)
                    call.sessions.set(SamlSession(requestId = "_auth_request_123"))
                    val result = call.samlLogout(
                        nameId = "user@example.com",
                        idpSloUrl = IDP_SLO_URL,
                        spMetadata = spMetadata,
                        sessionIndex = "_session123"
                    )
                    // Include the messageId in a header for tests to use when constructing LogoutResponse
                    call.response.header("X-Logout-Request-Id", result.messageId)
                    call.respondRedirect(result.redirectUrl)
                }

                get("/test-logout-with-relay") {
                    // Create a session for the test (normally this would be created during authentication)
                    call.sessions.set(SamlSession(requestId = "_auth_request_123"))
                    val result = call.samlLogout(
                        nameId = "user@example.com",
                        idpSloUrl = IDP_SLO_URL,
                        spMetadata = spMetadata,
                        sessionIndex = "_session123",
                        relayState = "/post-logout-page"
                    )
                    call.respondRedirect(result.redirectUrl)
                }
            }
        }
    }

    companion object {
        private const val SP_ENTITY_ID = "https://sp.example.com"
        private const val IDP_ENTITY_ID = "https://idp.example.com"
        private const val ACS_PATH = "/saml/acs"
        private const val ACS_URL = "http://localhost$ACS_PATH"
        private const val SLO_PATH = "/saml/slo"
        private const val SLO_URL = "http://localhost$SLO_PATH"
        private const val IDP_SSO_URL = "https://idp.example.com/sso"
        private const val IDP_SLO_URL = "https://idp.example.com/slo"

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
