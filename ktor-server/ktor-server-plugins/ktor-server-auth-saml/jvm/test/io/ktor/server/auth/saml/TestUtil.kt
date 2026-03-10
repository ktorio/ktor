/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.network.tls.certificates.*
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import org.opensaml.core.xml.io.MarshallingException
import org.opensaml.core.xml.schema.XSString
import org.opensaml.saml.common.SAMLVersion
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
     * Generates ECDSA test credentials for signing only (cannot be used for encryption).
     */
    fun generateEcdsaTestCredentials(): TestCredentials {
        val keyStore = generateCertificate(
            algorithm = "SHA256withECDSA",
            keyAlias = KEY_ALIAS,
            keyPassword = KEY_PASSWORD,
            keySizeInBits = 256
        )

        val privateKey = keyStore.getKey(KEY_ALIAS, KEY_PASSWORD.toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
        val keyPair = KeyPair(certificate.publicKey, privateKey)

        val credential = BasicX509Credential(certificate, privateKey)
        credential.entityId = "test-entity-ecdsa"

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

    /**
     * Creates a test IdP metadata with SLO support.
     */
    fun createTestIdPMetadata(
        entityId: String = "https://idp.example.com",
        ssoUrl: String = "https://idp.example.com/sso",
        sloUrl: String? = "https://idp.example.com/slo"
    ): IdPMetadata {
        val credentials = generateTestCredentials()
        return IdPMetadata {
            this.entityId = entityId
            this.ssoUrl = ssoUrl
            this.sloUrl = sloUrl
            this.signingCredentials = listOf(credentials.credential)
        }
    }

    /**
     * Creates a SAML LogoutResponse XML.
     *
     * @param inResponseTo The ID of the LogoutRequest being responded to
     * @param statusCode The SAML status code
     * @param statusMessage Optional status message
     * @param issuer The issuer entity ID (null to omit Issuer element)
     * @param destination The destination URL
     * @param issueInstant Custom IssueInstant (defaults to current time)
     */
    fun createLogoutResponse(
        inResponseTo: String,
        statusCode: String,
        statusMessage: String? = null,
        issuer: String? = null,
        destination: String,
        issueInstant: Instant = Clock.System.now()
    ): String {
        LibSaml.ensureInitialized()
        val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

        val issuerObj = issuer?.let {
            builderFactory.build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME) {
                value = it
            }
        }

        val statusCodeObj = builderFactory.build<StatusCode>(StatusCode.DEFAULT_ELEMENT_NAME) {
            value = statusCode
        }

        val statusMessageObj = statusMessage?.let {
            builderFactory.build<StatusMessage>(StatusMessage.DEFAULT_ELEMENT_NAME) {
                value = it
            }
        }

        val status = builderFactory.build<Status>(Status.DEFAULT_ELEMENT_NAME) {
            this.statusCode = statusCodeObj
            statusMessageObj?.let { this.statusMessage = it }
        }

        val logoutResponse = builderFactory.build<LogoutResponse>(LogoutResponse.DEFAULT_ELEMENT_NAME) {
            id = generateSecureSamlId()
            this.issueInstant = issueInstant.toJavaInstant()
            this.inResponseTo = inResponseTo
            this.destination = destination
            version = SAMLVersion.VERSION_20
            issuerObj?.let { this.issuer = it }
            this.status = status
        }

        return logoutResponse.marshalToString()
    }

    /**
     * Base64 encodes a string for HTTP-POST binding.
     */
    fun encodeForPost(xml: String): String = Base64.encode(source = xml.toByteArray())

    /**
     * Creates a SAML LogoutRequest XML for testing IdP-initiated logout.
     */
    fun createLogoutRequest(
        issuer: String? = null,
        destination: String,
        nameId: String,
        nameIdFormat: String? = null,
        sessionIndex: String? = null,
        issueInstant: Instant = Clock.System.now(),
        requestId: String = generateSecureSamlId()
    ): String {
        LibSaml.ensureInitialized()
        val builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory()

        val issuerObj = issuer?.let {
            builderFactory.build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME) {
                value = it
            }
        }

        val nameIdObj = builderFactory.build<NameID>(NameID.DEFAULT_ELEMENT_NAME) {
            value = nameId
            nameIdFormat?.let { format = it }
        }

        val sessionIndexElement = sessionIndex?.let {
            builderFactory.build<SessionIndex>(SessionIndex.DEFAULT_ELEMENT_NAME) {
                value = it
            }
        }

        val logoutRequest = builderFactory.build<LogoutRequest>(LogoutRequest.DEFAULT_ELEMENT_NAME) {
            id = requestId
            this.issueInstant = issueInstant.toJavaInstant()
            this.destination = destination
            version = SAMLVersion.VERSION_20
            issuerObj?.let { this.issuer = it }
            this.nameID = nameIdObj
            sessionIndexElement?.let { sessionIndexes.add(it) }
        }

        return logoutRequest.marshalToString()
    }

    fun createTestIdPMetadataWithSlo(
        entityId: String = "https://idp.example.com",
        ssoUrl: String = "https://idp.example.com/sso",
        sloUrl: String = "https://idp.example.com/slo",
        credentials: TestCredentials = sharedIdpCredentials
    ): IdPMetadata {
        return IdPMetadata {
            this.entityId = entityId
            this.ssoUrl = ssoUrl
            this.sloUrl = sloUrl
            this.signingCredentials = listOf(credentials.credential)
        }
    }

    /**
     * Creates a temporary keystore file from test credentials.
     * The file is marked for deletion on JVM exit.
     */
    fun createTempKeyStore(
        credentials: TestCredentials,
        storePassword: String = "test-pass",
        keyAlias: String = "test-key",
        keyPassword: String = "test-pass"
    ): java.io.File {
        val file = java.io.File.createTempFile("test-keystore", ".jks")
        file.deleteOnExit()
        credentials.saveToKeyStore(file, storePassword, keyAlias, keyPassword)
        return file
    }

    /**
     * Result of creating a signed SAML message for HTTP-Redirect binding.
     *
     * @property fullQueryString The complete query string including the Signature parameter
     * @property samlMessageBase64 The Base64-encoded (and deflated) SAML message
     */
    data class SignedRedirectMessage(
        val fullQueryString: String,
        val samlMessageBase64: String,
        val signatureBase64: String,
        val signatureAlgorithmUri: String,
    )

    /**
     * Creates a signed LogoutRequest for HTTP-Redirect binding testing.
     *
     * This creates the exact query string that would be signed by an IdP,
     * preserving the encoding to ensure signature verification succeeds.
     *
     * @param credentials The signing credentials
     * @param issuer The issuer entity ID
     * @param destination The SP's SLO URL
     * @param nameId The NameID of the subject to log out
     * @param sessionIndex Optional session index
     * @param relayState Optional RelayState
     * @param signatureAlgorithm The signature algorithm to use
     * @return SignedRedirectMessage containing all components needed for verification
     */
    fun createSignedLogoutRequestRedirect(
        credentials: TestCredentials,
        issuer: String = "https://idp.example.com",
        destination: String = "https://sp.example.com/saml/slo",
        nameId: String = "user@example.com",
        sessionIndex: String? = "_session123",
        relayState: String? = null,
        signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256
    ): SignedRedirectMessage {
        LibSaml.ensureInitialized()

        val logoutRequestXml = createLogoutRequest(
            issuer = issuer,
            destination = destination,
            nameId = nameId,
            sessionIndex = sessionIndex
        )

        // Deflate and Base64 encode for HTTP-Redirect binding
        val samlMessageBase64 = logoutRequestXml.toByteArray().deflateForRedirect()

        // Build the query string in the order required by SAML spec
        val enc = "UTF-8"
        val queryParts = mutableListOf<String>()
        queryParts.add("SAMLRequest=${java.net.URLEncoder.encode(samlMessageBase64, enc)}")
        if (relayState != null) {
            queryParts.add("RelayState=${java.net.URLEncoder.encode(relayState, enc)}")
        }
        queryParts.add("SigAlg=${java.net.URLEncoder.encode(signatureAlgorithm.uri, enc)}")
        val queryStringWithoutSignature = queryParts.joinToString("&")

        // Sign the query string
        val signature = signQueryString(queryStringWithoutSignature, credentials.credential, signatureAlgorithm)
        val fullQueryString = "$queryStringWithoutSignature&Signature=${java.net.URLEncoder.encode(signature, enc)}"

        return SignedRedirectMessage(
            fullQueryString = fullQueryString,
            samlMessageBase64 = samlMessageBase64,
            signatureBase64 = signature,
            signatureAlgorithmUri = signatureAlgorithm.uri
        )
    }

    /**
     * Creates a signed LogoutResponse for HTTP-Redirect binding testing.
     *
     * @param credentials The signing credentials
     * @param inResponseTo The ID of the LogoutRequest being responded to
     * @param statusCode The SAML status code
     * @param issuer The issuer entity ID
     * @param destination The SP's SLO URL
     * @param relayState Optional RelayState
     * @param signatureAlgorithm The signature algorithm to use
     * @return SignedRedirectMessage containing all components needed for verification
     */
    fun createSignedLogoutResponseRedirect(
        credentials: TestCredentials,
        inResponseTo: String = "_request123",
        statusCode: String = StatusCode.SUCCESS,
        issuer: String = "https://idp.example.com",
        destination: String = "https://sp.example.com/saml/slo",
        relayState: String? = null,
        signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256
    ): SignedRedirectMessage {
        LibSaml.ensureInitialized()

        val logoutResponseXml = createLogoutResponse(
            inResponseTo = inResponseTo,
            statusCode = statusCode,
            issuer = issuer,
            destination = destination
        )

        // Deflate and Base64 encode for HTTP-Redirect binding
        val samlMessageBase64 = logoutResponseXml.toByteArray().deflateForRedirect()

        // Build the query string in the order required by SAML spec
        val enc = "UTF-8"
        val queryParts = mutableListOf<String>()
        queryParts.add("SAMLResponse=${java.net.URLEncoder.encode(samlMessageBase64, enc)}")
        if (relayState != null) {
            queryParts.add("RelayState=${java.net.URLEncoder.encode(relayState, enc)}")
        }
        queryParts.add("SigAlg=${java.net.URLEncoder.encode(signatureAlgorithm.uri, enc)}")
        val queryStringWithoutSignature = queryParts.joinToString("&")

        // Sign the query string
        val signature = signQueryString(queryStringWithoutSignature, credentials.credential, signatureAlgorithm)
        val fullQueryString = "$queryStringWithoutSignature&Signature=${java.net.URLEncoder.encode(signature, enc)}"

        return SignedRedirectMessage(
            fullQueryString = fullQueryString,
            samlMessageBase64 = samlMessageBase64,
            signatureBase64 = signature,
            signatureAlgorithmUri = signatureAlgorithm.uri
        )
    }

    /**
     * Deflates and Base64 encodes bytes for HTTP-Redirect binding.
     */
    private fun ByteArray.deflateForRedirect(): String {
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(this)
        deflater.finish()

        val outputStream = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()

        return Base64.encode(source = outputStream.toByteArray())
    }
}
