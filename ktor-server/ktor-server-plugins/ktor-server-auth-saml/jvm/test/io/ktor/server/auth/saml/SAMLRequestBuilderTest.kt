/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import java.net.URLEncoder
import kotlin.test.*

class SamlRequestBuilderTest {

    @Test
    fun `test buildAuthnRequestRedirect generates valid request ID and URL`() {
        val spEntityId = "test-sp"
        val acsUrl = "http://localhost:8080/saml/acs"
        val idpSsoUrl = "http://idp.example.com/sso"

        val result = buildAuthnRequestRedirect(
            spEntityId = spEntityId,
            acsUrl = acsUrl,
            idpSsoUrl = idpSsoUrl
        )

        assertNotNull(result.messageId)
        assertTrue(result.messageId.startsWith("_"))
        assertTrue(result.redirectUrl.startsWith(idpSsoUrl))
        assertTrue(result.redirectUrl.contains("SAMLRequest="))
        assertTrue(result.redirectUrl.startsWith("$idpSsoUrl?"))

        assertFalse(result.redirectUrl.contains("Signature="))
        assertFalse(result.redirectUrl.contains("SigAlg="))
    }

    @Test
    fun `test buildAuthnRequestRedirect includes RelayState when provided`() {
        val spEntityId = "test-sp"
        val acsUrl = "http://localhost:8080/saml/acs"
        val idpSsoUrl = "http://idp.example.com/sso"
        val relayState = "/protected/resource"

        val result = buildAuthnRequestRedirect(
            spEntityId = spEntityId,
            acsUrl = acsUrl,
            idpSsoUrl = idpSsoUrl,
            relayState = relayState
        )

        assertTrue(result.redirectUrl.contains("RelayState="))
        assertTrue(result.redirectUrl.contains(URLEncoder.encode(relayState, "UTF-8")))
    }

    @Test
    fun `test buildAuthnRequestRedirect generates unique request IDs`() {
        val spEntityId = "test-sp"
        val acsUrl = "http://localhost:8080/saml/acs"
        val idpSsoUrl = "http://idp.example.com/sso"

        val result1 = buildAuthnRequestRedirect(
            spEntityId = spEntityId,
            acsUrl = acsUrl,
            idpSsoUrl = idpSsoUrl
        )

        val result2 = buildAuthnRequestRedirect(
            spEntityId = spEntityId,
            acsUrl = acsUrl,
            idpSsoUrl = idpSsoUrl
        )

        assertNotEquals(result1.messageId, result2.messageId)
    }

    @Test
    fun `test buildAuthnRequestRedirect with forceAuthn`() {
        val spEntityId = "test-sp"
        val acsUrl = "http://localhost:8080/saml/acs"
        val idpSsoUrl = "http://idp.example.com/sso"

        // Test with forceAuthn enabled
        val result = buildAuthnRequestRedirect(
            spEntityId = spEntityId,
            acsUrl = acsUrl,
            idpSsoUrl = idpSsoUrl,
            forceAuthn = true
        )

        assertNotNull(result.messageId)
        assertTrue(result.redirectUrl.contains("SAMLRequest="))
    }

    @Test
    fun `test NameIdFormat constants`() {
        assertEquals("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress", NameIdFormat.Email.uri)
        assertEquals("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent", NameIdFormat.Persistent.uri)
        assertEquals("urn:oasis:names:tc:SAML:2.0:nameid-format:transient", NameIdFormat.Transient.uri)
        assertEquals("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified", NameIdFormat.Unspecified.uri)
        assertEquals("urn:custom:format", NameIdFormat("urn:custom:format").uri)
    }
}
