/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import org.opensaml.saml.common.xml.SAMLConstants
import org.opensaml.saml.saml2.metadata.*
import org.opensaml.security.credential.UsageType
import org.opensaml.xmlsec.signature.KeyInfo
import org.opensaml.xmlsec.signature.X509Certificate
import org.opensaml.xmlsec.signature.X509Data
import kotlin.io.encoding.Base64
import java.security.cert.X509Certificate as JavaX509Certificate
import org.opensaml.saml.saml2.metadata.ContactPerson as OpenSamlContactPerson

/**
 * Generates SAML Service Provider metadata XML.
 *
 * @return XML string representing the SP metadata
 * @throws IllegalArgumentException if required fields (spEntityId, acsUrl) are missing
 */
public fun SamlSpMetadata.toXml(): String {
    LibSaml.ensureInitialized()
    require(!spEntityId.isNullOrBlank()) { "spEntityId is required for SP metadata" }
    require(acsUrl.isNotBlank()) { "acsUrl is required for SP metadata" }

    val entityDescriptor = buildEntityDescriptor(metadata = this)
    return entityDescriptor.marshalToString()
}

private fun buildEntityDescriptor(metadata: SamlSpMetadata): EntityDescriptor {
    val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

    val acs = builderFactory.build<AssertionConsumerService>(AssertionConsumerService.DEFAULT_ELEMENT_NAME) {
        binding = SAMLConstants.SAML2_POST_BINDING_URI
        location = metadata.acsUrl
        index = 0
        setIsDefault(true)
    }

    val signingCertificate = metadata.signingCredential?.entityCertificate
    val spDescriptor = builderFactory.build<SPSSODescriptor>(SPSSODescriptor.DEFAULT_ELEMENT_NAME) {
        addSupportedProtocol(SAMLConstants.SAML20P_NS)
        isAuthnRequestsSigned = signingCertificate != null
        wantAssertionsSigned = metadata.wantAssertionsSigned
        assertionConsumerServices.add(acs)

        // Add SLO endpoint if configured
        val sloUrl = metadata.sloUrl
        if (sloUrl.isNotBlank()) {
            val uris = listOf(SAMLConstants.SAML2_REDIRECT_BINDING_URI, SAMLConstants.SAML2_POST_BINDING_URI)
            val services = uris.map { bindingUri ->
                builderFactory.build<SingleLogoutService>(SingleLogoutService.DEFAULT_ELEMENT_NAME) {
                    binding = bindingUri
                    location = sloUrl
                }
            }
            singleLogoutServices.addAll(services)
        }

        metadata.supportedNameIdFormats.forEach { format ->
            val nameIdFormat = builderFactory.build<NameIDFormat>(NameIDFormat.DEFAULT_ELEMENT_NAME) {
                uri = format.uri
            }
            nameIDFormats.add(nameIdFormat)
        }

        signingCertificate?.let { certificate ->
            keyDescriptors.add(buildKeyDescriptor(certificate, UsageType.SIGNING))
        }

        val encryptionCertificate = metadata.encryptionCredential?.entityCertificate
            ?: signingCertificate?.takeIf { metadata.signingCredential?.supportsDecryption == true }
        encryptionCertificate?.let { certificate ->
            keyDescriptors.add(buildKeyDescriptor(certificate, UsageType.ENCRYPTION))
        }
    }

    return builderFactory.build(EntityDescriptor.DEFAULT_ELEMENT_NAME) {
        entityID = metadata.spEntityId
        roleDescriptors.add(spDescriptor)

        metadata.toOrganization()?.let {
            organization = it
        }
        metadata.technicalContacts.forEach { contact ->
            contactPersons.add(contact.toOpenSaml(type = ContactPersonTypeEnumeration.TECHNICAL))
        }
        metadata.supportContacts.forEach { contact ->
            contactPersons.add(contact.toOpenSaml(type = ContactPersonTypeEnumeration.SUPPORT))
        }
    }
}

private fun buildKeyDescriptor(certificate: JavaX509Certificate, usage: UsageType): KeyDescriptor {
    val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

    val x509Cert = builderFactory.buildXmlObject<X509Certificate>(X509Certificate.DEFAULT_ELEMENT_NAME) {
        value = Base64.encode(certificate.encoded)
    }
    val x509Data = builderFactory.buildXmlObject<X509Data>(X509Data.DEFAULT_ELEMENT_NAME) {
        x509Certificates.add(x509Cert)
    }
    val keyInfo = builderFactory.buildXmlObject<KeyInfo>(KeyInfo.DEFAULT_ELEMENT_NAME) {
        x509Datas.add(x509Data)
    }

    return builderFactory.build(KeyDescriptor.DEFAULT_ELEMENT_NAME) {
        use = usage
        this.keyInfo = keyInfo
    }
}

private fun SamlContactPerson.toOpenSaml(type: ContactPersonTypeEnumeration): OpenSamlContactPerson {
    val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()
    val contact = this

    return builderFactory.build(OpenSamlContactPerson.DEFAULT_ELEMENT_NAME) {
        this.type = type

        contact.givenName?.let {
            givenName = builderFactory.build(GivenName.DEFAULT_ELEMENT_NAME) { value = it }
        }

        contact.surname?.let {
            surName = builderFactory.build(SurName.DEFAULT_ELEMENT_NAME) { value = it }
        }

        contact.emailAddress?.let {
            val email = builderFactory.build<EmailAddress>(EmailAddress.DEFAULT_ELEMENT_NAME) {
                uri = contact.emailAddress
            }
            emailAddresses.add(email)
        }

        contact.telephoneNumber?.let {
            val phone = builderFactory.build<TelephoneNumber>(TelephoneNumber.DEFAULT_ELEMENT_NAME) {
                value = contact.telephoneNumber
            }
            telephoneNumbers.add(phone)
        }
    }
}

private fun SamlSpMetadata.toOrganization(): Organization? {
    if (organizationUrl == null && organizationName == null && organizationDisplayName == null) {
        return null
    }
    val factory = XMLObjectProviderRegistrySupport.getBuilderFactory()
    return factory.build(Organization.DEFAULT_ELEMENT_NAME) {
        organizationName?.let {
            val name = factory.build<OrganizationName>(OrganizationName.DEFAULT_ELEMENT_NAME) {
                value = it
                xmlLang = "en"
            }
            organizationNames.add(name)
        }

        organizationDisplayName?.let {
            val displayName = factory.build<OrganizationDisplayName>(OrganizationDisplayName.DEFAULT_ELEMENT_NAME) {
                value = it
                xmlLang = "en"
            }
            displayNames.add(displayName)
        }

        organizationUrl?.let {
            val url = factory.build<OrganizationURL>(OrganizationURL.DEFAULT_ELEMENT_NAME) {
                uri = it
                xmlLang = "en"
            }
            urLs.add(url)
        }
    }
}
