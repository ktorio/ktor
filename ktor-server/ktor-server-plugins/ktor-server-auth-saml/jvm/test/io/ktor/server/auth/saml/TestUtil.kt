/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.network.tls.certificates.*
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import org.opensaml.core.xml.io.MarshallingException
import org.opensaml.core.xml.schema.XSString
import org.opensaml.saml.saml2.core.*
import org.opensaml.saml.saml2.encryption.Encrypter
import org.opensaml.security.credential.Credential
import org.opensaml.security.x509.BasicX509Credential
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters
import org.opensaml.xmlsec.encryption.support.EncryptionConstants
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory
import org.opensaml.xmlsec.signature.Signature
import org.opensaml.xmlsec.signature.support.SignatureConstants
import org.opensaml.xmlsec.signature.support.Signer
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Test utilities for SAML tests.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
object SamlTestUtils {

    /**
     * Shared IDP credentials for tests that don't need unique credentials.
     */
    val sharedIdpCredentials: TestCredentials by lazy { generateTestCredentials() }

    /**
     * Shared SP credentials for tests that don't need unique credentials.
     */
    val sharedSpCredentials: TestCredentials by lazy { generateTestCredentials() }

    /**
     * Test credential holder for signing and encryption operations.
     */
    data class TestCredentials(
        val credential: BasicX509Credential,
        val keyPair: KeyPair,
        val certificate: X509Certificate
    ) {
        /**
         * Saves these credentials to a keystore file.
         */
        fun saveToKeyStore(file: java.io.File, storePassword: String, keyAlias: String, keyPassword: String) {
            val keyStore = KeyStore.getInstance("JKS")
            keyStore.load(null, storePassword.toCharArray())
            keyStore.setKeyEntry(
                keyAlias,
                keyPair.private,
                keyPassword.toCharArray(),
                arrayOf(certificate)
            )
            keyStore.saveToFile(file, storePassword)
        }
    }

    private const val KEY_ALIAS = "test_key"
    private const val KEY_PASSWORD = "test_pass"

    fun generateTestCredentials(): TestCredentials = generateRsaTestCredentials()

    /**
     * Generates RSA test credentials for signing and encryption.
     */
    fun generateRsaTestCredentials(): TestCredentials {
        val keyStore = generateCertificate(
            algorithm = "SHA256withRSA",
            keyAlias = KEY_ALIAS,
            keyPassword = KEY_PASSWORD,
            keySizeInBits = 2048
        )

        val privateKey = keyStore.getKey(KEY_ALIAS, KEY_PASSWORD.toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
        val keyPair = KeyPair(certificate.publicKey, privateKey)

        val credential = BasicX509Credential(certificate, privateKey)
        credential.entityId = "test-entity"

        return TestCredentials(credential, keyPair, certificate)
    }

    /**
     * Signs a SAML assertion using the provided credential.
     */
    fun signAssertion(assertion: Assertion, credential: Credential) {
        val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

        val keyInfoGeneratorFactory = X509KeyInfoGeneratorFactory()
        keyInfoGeneratorFactory.setEmitEntityCertificate(true)
        val keyInfoGenerator = keyInfoGeneratorFactory.newInstance()

        val signature = builderFactory.buildXmlObject<Signature>(Signature.DEFAULT_ELEMENT_NAME) {
            signingCredential = credential
            signatureAlgorithm = SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256
            canonicalizationAlgorithm = SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            keyInfo = keyInfoGenerator.generate(credential)
        }

        assertion.signature = signature

        val marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory()
        val marshaller = marshallerFactory.getMarshaller(assertion)
            ?: throw MarshallingException("No marshaller found for Assertion")
        marshaller.marshall(assertion)

        Signer.signObject(signature)
    }

    /**
     * Signs a SAML response using the provided credential.
     */
    fun signResponse(response: Response, credential: Credential) {
        LibSaml.ensureInitialized()
        val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

        val keyInfoGeneratorFactory = X509KeyInfoGeneratorFactory()
        keyInfoGeneratorFactory.setEmitEntityCertificate(true)
        val keyInfoGenerator = keyInfoGeneratorFactory.newInstance()

        val signature = builderFactory.buildXmlObject<Signature>(Signature.DEFAULT_ELEMENT_NAME) {
            signingCredential = credential
            signatureAlgorithm = SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256
            canonicalizationAlgorithm = SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            keyInfo = keyInfoGenerator.generate(credential)
        }

        response.signature = signature

        val marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory()
        val marshaller = marshallerFactory.getMarshaller(response)
            ?: throw MarshallingException("No marshaller found for Response")
        marshaller.marshall(response)

        Signer.signObject(signature)
    }

    /**
     * Encrypts a SAML assertion using the provided credential.
     */
    fun encryptAssertion(assertion: Assertion, encryptionCredential: Credential): EncryptedAssertion {
        LibSaml.ensureInitialized()
        val dataEncryptionParams = DataEncryptionParameters()
        dataEncryptionParams.algorithm = EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES256_GCM

        val keyEncryptionParams = KeyEncryptionParameters()
        keyEncryptionParams.encryptionCredential = encryptionCredential
        keyEncryptionParams.algorithm = EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP

        val keyInfoGeneratorFactory = X509KeyInfoGeneratorFactory()
        keyInfoGeneratorFactory.setEmitEntityCertificate(true)
        keyEncryptionParams.keyInfoGenerator = keyInfoGeneratorFactory.newInstance()

        val encrypter = Encrypter(dataEncryptionParams, keyEncryptionParams)
        encrypter.keyPlacement = Encrypter.KeyPlacement.PEER

        return encrypter.encrypt(assertion)
    }

    fun encodeResponseToBase64(response: Response): String {
        return Base64.encode(source = response.marshalToString().toByteArray())
    }

    /**
     * Creates a minimal valid SAML assertion for testing.
     */
    fun createTestAssertion(
        nameId: String = "test-user@example.com",
        issuerEntityId: String = "test-idp",
        audienceEntityId: String? = null,
        recipientUrl: String? = null,
        inResponseTo: String? = null,
        attributes: Map<String, List<String>> = emptyMap(),
        sessionIndex: String? = "session-" + Uuid.random().toString(),
        notBefore: Instant = Clock.System.now() - 60.seconds,
        notOnOrAfter: Instant = Clock.System.now() + 300.seconds
    ): Assertion {
        LibSaml.ensureInitialized()
        val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

        val issuer = builderFactory.build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME) {
            value = issuerEntityId
        }

        val nameIDObj = builderFactory.build<NameID>(NameID.DEFAULT_ELEMENT_NAME) {
            value = nameId
            format = NameIDType.EMAIL
        }

        // Note: SubjectConfirmationData for Bearer method should NOT have NotBefore
        // per SAML 2.0 spec (section 2.4.1.2). Only NotOnOrAfter is allowed.
        val subjectConfirmationData = builderFactory.build<SubjectConfirmationData>(
            SubjectConfirmationData.DEFAULT_ELEMENT_NAME
        ) {
            this.notOnOrAfter = notOnOrAfter.toJavaInstant()
            recipientUrl?.let { this.recipient = it }
            inResponseTo?.let { this.inResponseTo = it }
        }

        val subjectConfirmation = builderFactory.build<SubjectConfirmation>(SubjectConfirmation.DEFAULT_ELEMENT_NAME) {
            method = SubjectConfirmation.METHOD_BEARER
            this.subjectConfirmationData = subjectConfirmationData
        }

        val subject = builderFactory.build<Subject>(Subject.DEFAULT_ELEMENT_NAME) {
            this.nameID = nameIDObj
            subjectConfirmations.add(subjectConfirmation)
        }

        val conditions = builderFactory.build<Conditions>(Conditions.DEFAULT_ELEMENT_NAME) {
            this.notBefore = notBefore.toJavaInstant()
            this.notOnOrAfter = notOnOrAfter.toJavaInstant()

            // Add AudienceRestriction if audience is provided
            if (audienceEntityId != null) {
                val audience = builderFactory.build<Audience>(Audience.DEFAULT_ELEMENT_NAME) {
                    uri = audienceEntityId
                }
                val audienceRestriction = builderFactory.build<AudienceRestriction>(
                    AudienceRestriction.DEFAULT_ELEMENT_NAME
                ) {
                    audiences.add(audience)
                }
                audienceRestrictions.add(audienceRestriction)
            }
        }

        val authnStatement = sessionIndex?.let {
            builderFactory.build<AuthnStatement>(AuthnStatement.DEFAULT_ELEMENT_NAME) {
                this.authnInstant = Clock.System.now().toJavaInstant()
                this.sessionIndex = it
            }
        }

        val attributeStatement = if (attributes.isNotEmpty()) {
            builderFactory.build<AttributeStatement>(AttributeStatement.DEFAULT_ELEMENT_NAME) {
                attributes.forEach { (name, values) ->
                    val attribute = builderFactory.build<Attribute>(Attribute.DEFAULT_ELEMENT_NAME) {
                        this.name = name
                    }
                    values.forEach { value ->
                        val v = builderFactory.buildXmlObject<XSString>(AttributeValue.DEFAULT_ELEMENT_NAME) {
                            this.value = value
                        }
                        attribute.attributeValues.add(v)
                    }
                    this.attributes.add(attribute)
                }
            }
        } else {
            null
        }

        return builderFactory.build(Assertion.DEFAULT_ELEMENT_NAME) {
            id = generateSecureSamlId()
            issueInstant = Clock.System.now().toJavaInstant()
            this.issuer = issuer
            this.subject = subject
            this.conditions = conditions
            authnStatement?.let { authnStatements.add(it) }
            attributeStatement?.let { attributeStatements.add(it) }
        }
    }

    fun createTestResponse(
        assertion: Assertion,
        issuerEntityId: String = "test-idp",
        inResponseTo: String? = null,
        statusCode: String = StatusCode.SUCCESS,
        destination: String? = null
    ): Response {
        LibSaml.ensureInitialized()
        val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

        val issuer = builderFactory.build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME) {
            value = issuerEntityId
        }

        val statusCodeObj = builderFactory.build<StatusCode>(StatusCode.DEFAULT_ELEMENT_NAME) {
            value = statusCode
        }

        val status = builderFactory.build<Status>(Status.DEFAULT_ELEMENT_NAME) {
            this.statusCode = statusCodeObj
        }

        return builderFactory.build(Response.DEFAULT_ELEMENT_NAME) {
            id = generateSecureSamlId()
            issueInstant = Clock.System.now().toJavaInstant()
            inResponseTo?.let { this.inResponseTo = it }
            destination?.let { this.destination = it }
            this.issuer = issuer
            this.status = status
            assertions.add(assertion)
        }
    }

    /**
     * Creates a test SAML response with an encrypted assertion.
     *
     * @param encryptedAssertion The encrypted assertion to include
     * @param issuerEntityId The entity ID of the issuer (IdP)
     * @param inResponseTo The InResponseTo value (request ID)
     * @param destination The Destination attribute (ACS URL)
     */
    fun createTestResponseWithEncryptedAssertion(
        encryptedAssertion: EncryptedAssertion,
        issuerEntityId: String = "test-idp",
        inResponseTo: String? = null,
        destination: String? = null
    ): Response {
        LibSaml.ensureInitialized()
        val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

        val issuer = builderFactory.build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME) {
            value = issuerEntityId
        }

        val statusCode = builderFactory.build<StatusCode>(StatusCode.DEFAULT_ELEMENT_NAME) {
            value = StatusCode.SUCCESS
        }

        val status = builderFactory.build<Status>(Status.DEFAULT_ELEMENT_NAME) {
            this.statusCode = statusCode
        }

        return builderFactory.build(Response.DEFAULT_ELEMENT_NAME) {
            id = generateSecureSamlId()
            issueInstant = Clock.System.now().toJavaInstant()
            inResponseTo?.let { this.inResponseTo = it }
            destination?.let { this.destination = it }
            this.issuer = issuer
            this.status = status
            encryptedAssertions.add(encryptedAssertion)
        }
    }
}
