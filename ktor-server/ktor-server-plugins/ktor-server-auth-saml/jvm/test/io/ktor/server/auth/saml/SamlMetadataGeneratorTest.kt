/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import kotlin.test.*

class SamlSpMetadataGeneratorTest {

    @BeforeTest
    fun setup() {
        LibSaml.ensureInitialized()
    }

    @Test
    fun `test generates minimal SP metadata`() {
        val spMetadata = SamlSpMetadata {
            spEntityId = "https://sp.example.com"
            acsUrl = "https://sp.example.com/saml/acs"
            sloUrl = ""
            supportedNameIdFormats = emptyList()
        }
        val metadata = spMetadata.toXml()

        val expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="https://sp.example.com">
                <md:SPSSODescriptor AuthnRequestsSigned="false" WantAssertionsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://sp.example.com/saml/acs" index="0" isDefault="true"/>
                </md:SPSSODescriptor>
            </md:EntityDescriptor>
        """.trimIndent()

        assertEquals(expected.normalizeXml(), metadata.normalizeXml())
    }

    @Test
    fun `test generates complete SP metadata with all options`() {
        val credentials = SamlTestUtils.generateTestCredentials()
        val tempKeyStore = SamlTestUtils.createTempKeyStore(credentials)

        val spMetadata = SamlSpMetadata {
            spEntityId = "https://sp.example.com/saml/metadata"
            acsUrl = "https://sp.example.com/saml/acs"
            sloUrl = "https://sp.example.com/saml/slo"
            signingCredential = SamlCrypto.loadCredential(
                keystorePath = tempKeyStore.absolutePath,
                keystorePassword = "test-pass",
                keyAlias = "test-key",
                keyPassword = "test-pass"
            )
            supportedNameIdFormats = listOf(
                NameIdFormat.Email,
                NameIdFormat.Persistent,
                NameIdFormat.Transient
            )
            wantAssertionsSigned = true
            organizationName = "Example Corp"
            organizationDisplayName = "Example Corporation"
            organizationUrl = "https://example.com"
            technicalContact {
                givenName = "Tech"
                surname = "Support"
                emailAddress = "tech@example.com"
                telephoneNumber = "+1-555-1234"
            }
            supportContact {
                givenName = "Help"
                surname = "Desk"
                emailAddress = "help@example.com"
            }
        }
        val metadata = spMetadata.toXml()

        val expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="https://sp.example.com/saml/metadata">
                <md:SPSSODescriptor AuthnRequestsSigned="true" WantAssertionsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:KeyDescriptor use="signing">
                        <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                            <ds:X509Data>
                                <ds:X509Certificate>{{CERTIFICATE}}</ds:X509Certificate>
                            </ds:X509Data>
                        </ds:KeyInfo>
                    </md:KeyDescriptor>
                    <md:KeyDescriptor use="encryption">
                        <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                            <ds:X509Data>
                                <ds:X509Certificate>{{CERTIFICATE}}</ds:X509Certificate>
                            </ds:X509Data>
                        </ds:KeyInfo>
                    </md:KeyDescriptor>
                    <md:SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://sp.example.com/saml/slo"/>
                    <md:SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://sp.example.com/saml/slo"/>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:persistent</md:NameIDFormat>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:transient</md:NameIDFormat>
                    <md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://sp.example.com/saml/acs" index="0" isDefault="true"/>
                </md:SPSSODescriptor>
                <md:Organization>
                    <md:OrganizationName xml:lang="en">Example Corp</md:OrganizationName>
                    <md:OrganizationDisplayName xml:lang="en">Example Corporation</md:OrganizationDisplayName>
                    <md:OrganizationURL xml:lang="en">https://example.com</md:OrganizationURL>
                </md:Organization>
                <md:ContactPerson contactType="technical">
                    <md:GivenName>Tech</md:GivenName>
                    <md:SurName>Support</md:SurName>
                    <md:EmailAddress>tech@example.com</md:EmailAddress>
                    <md:TelephoneNumber>+1-555-1234</md:TelephoneNumber>
                </md:ContactPerson>
                <md:ContactPerson contactType="support">
                    <md:GivenName>Help</md:GivenName>
                    <md:SurName>Desk</md:SurName>
                    <md:EmailAddress>help@example.com</md:EmailAddress>
                </md:ContactPerson>
            </md:EntityDescriptor>
        """.trimIndent()

        assertEquals(expected.normalizeXml(), metadata.normalizeXml().maskCertificates())
    }

    @Test
    fun `test validation fails for invalid configuration`() {
        assertFailsWith<IllegalArgumentException>("spEntityId is required") {
            SamlSpMetadata {
                acsUrl = "https://sp.example.com/saml/acs"
            }.toXml()
        }

        assertFailsWith<IllegalArgumentException>("acsUrl is required") {
            SamlSpMetadata {
                spEntityId = "https://sp.example.com"
                acsUrl = ""
            }.toXml()
        }
    }

    @Test
    fun `metadata generator includes encryption certificate for RSA signing credential`() {
        val rsaCredential = SamlTestUtils.generateRsaTestCredentials().credential

        val spMetadata = SamlSpMetadata {
            spEntityId = "https://sp.example.com"
            acsUrl = "https://sp.example.com/saml/acs"
            signingCredential = rsaCredential
            // No separate encryption credential - should use signing credential
        }

        val xml = spMetadata.toXml()

        // Should contain both signing and encryption key descriptors (using the same RSA cert)
        assertTrue(xml.contains("use=\"signing\""), "Metadata should contain signing key descriptor")
        assertTrue(xml.contains("use=\"encryption\""), "Metadata should contain encryption key descriptor")
    }

    @Test
    fun `metadata generator omits encryption certificate for ECDSA signing without encryption credential`() {
        val ecdsaSigningCredential = SamlTestUtils.generateEcdsaTestCredentials().credential

        val spMetadata = SamlSpMetadata {
            spEntityId = "https://sp.example.com"
            acsUrl = "https://sp.example.com/saml/acs"
            signingCredential = ecdsaSigningCredential
            // No encryption credential
        }

        val xml = spMetadata.toXml()

        assertTrue(xml.contains("use=\"signing\""), "Metadata should contain signing key descriptor")
        assertFalse(xml.contains("use=\"encryption\""), "Metadata should not contain encryption key descriptor")
    }

    private fun String.normalizeXml(): String =
        replace(Regex(">[\\s\\n]+<"), "><").trim()

    private fun String.maskCertificates(): String =
        replace(
            Regex("<ds:X509Certificate>[^<]+</ds:X509Certificate>"),
            "<ds:X509Certificate>{{CERTIFICATE}}</ds:X509Certificate>"
        )
}
