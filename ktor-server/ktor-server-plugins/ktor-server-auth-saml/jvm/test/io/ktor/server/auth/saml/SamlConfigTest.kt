/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.network.tls.certificates.*
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
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
        private val TEST_KEY_STORE = buildKeyStore {
            certificate("test") {
                password = "test"
            }
        }

        private val TEST_CERTIFICATE = TEST_KEY_STORE.getCertificate("test") as X509Certificate

        private val TEST_CERTIFICATE_BASE64 = Base64.encode(TEST_CERTIFICATE.encoded)

        private val MINIMAL_IDP_METADATA = """
        <?xml version="1.0" encoding="UTF-8"?>
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
                <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                                     Location="https://idp.example.com/sso"/>
            </IDPSSODescriptor>
        </EntityDescriptor>
        """.trimIndent()

        private val TEST_IDP_METADATA = parseSamlIdpMetadata(xml = MINIMAL_IDP_METADATA)
    }
}
