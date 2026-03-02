/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class SamlConfigTest {
    @Test
    fun `test default SamlSpMetadata values`() {
        val metadata = SamlSpMetadata {}

        assertEquals(null, metadata.spEntityId)
        assertEquals("/saml/acs", metadata.acsUrl)
        assertEquals("/saml/slo", metadata.sloUrl)
        assertTrue(metadata.wantAssertionsSigned)
    }

    @Test
    fun `test default SamlConfig values`() {
        val config = SamlConfig(name = "test", description = null)

        assertEquals(null, config.sp)
        assertEquals(60.seconds, config.clockSkew)
        assertNull(config.replayCache)
        assertTrue(config.requireSignedLogoutRequest)
    }

    @Test
    fun `test SamlSpMetadata with custom values`() {
        val metadata = SamlSpMetadata {
            spEntityId = "custom-sp"
            acsUrl = "https://example.com/acs"
            sloUrl = "https://example.com/slo"
            wantAssertionsSigned = false
        }

        assertEquals("custom-sp", metadata.spEntityId)
        assertEquals("https://example.com/acs", metadata.acsUrl)
        assertEquals("https://example.com/slo", metadata.sloUrl)
        assertFalse(metadata.wantAssertionsSigned)
    }

    @Test
    fun `test idpMetadata parsing`() {
        assertEquals("https://idp.example.com", TEST_IDP_METADATA.entityId)
        assertEquals("https://idp.example.com/sso", TEST_IDP_METADATA.ssoUrl)
    }

    companion object {
        private val MINIMAL_IDP_METADATA = """
        <?xml version="1.0" encoding="UTF-8"?>
        <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata"
                          entityID="https://idp.example.com">
            <IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                                     Location="https://idp.example.com/sso"/>
            </IDPSSODescriptor>
        </EntityDescriptor>
        """.trimIndent()

        private val TEST_IDP_METADATA = parseSamlIdpMetadata(xml = MINIMAL_IDP_METADATA)
    }
}
