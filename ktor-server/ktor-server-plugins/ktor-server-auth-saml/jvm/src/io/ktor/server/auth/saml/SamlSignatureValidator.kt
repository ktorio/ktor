/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.saml.common.SignableSAMLObject
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator
import org.opensaml.security.credential.Credential
import org.opensaml.security.credential.MutableCredential
import org.opensaml.security.credential.impl.CollectionCredentialResolver
import org.opensaml.xmlsec.config.impl.DefaultSecurityConfigurationBootstrap
import org.opensaml.xmlsec.signature.support.SignatureValidator
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine
import org.opensaml.xmlsec.signature.support.impl.SignatureAlgorithmValidator
import java.security.Signature
import kotlin.io.encoding.Base64

/**
 * Verifies XML signatures on SAML objects.
 *
 * This class provides comprehensive signature validation:
 * 1. Validates signature and digest algorithms against allowlists (if configured)
 * 2. SAML signature profile validation (prevents XSW attacks)
 * 3. Cryptographic signature verification against IdP credentials
 */
internal class SamlSignatureVerifier(
    idpMetadata: IdPMetadata,
    private val allowedSignatureAlgorithms: Set<SignatureAlgorithm>? = null,
    allowedDigestAlgorithms: Set<DigestAlgorithm>? = null
) {
    /**
     * Credentials with entity ID set for proper credential resolution.
     */
    val credentials: List<Credential> = idpMetadata.signingCredentials.map { credential ->
        credential.apply {
            if (this is MutableCredential) {
                entityId = idpMetadata.entityId
            }
        }
    }

    private val algorithmValidator: SignatureAlgorithmValidator? =
        if (allowedSignatureAlgorithms != null || allowedDigestAlgorithms != null) {
            val whitelistAlgorithms = buildSet {
                allowedSignatureAlgorithms?.let { algorithms -> addAll(algorithms.map { it.uri }) }
                allowedDigestAlgorithms?.let { algorithms -> addAll(algorithms.map { it.uri }) }
            }
            SignatureAlgorithmValidator(whitelistAlgorithms, null)
        } else {
            null
        }

    val signatureTrustEngine = run {
        val credentialResolver = CollectionCredentialResolver(credentials)
        val keyInfoResolver = DefaultSecurityConfigurationBootstrap
            .buildBasicInlineKeyInfoCredentialResolver()
        ExplicitKeySignatureTrustEngine(credentialResolver, keyInfoResolver)
    }

    val signatureProfileValidator = SAMLSignatureProfileValidator()

    /**
     * Verifies the XML signature on a SignableSAMLObject.
     */
    fun verify(signedObject: SignableSAMLObject) {
        val signature = samlRequire(signedObject.signature) { "No signature to verify" }

        if (algorithmValidator != null) {
            try {
                algorithmValidator.validate(signature)
            } catch (e: Exception) {
                throw SamlValidationException("Algorithm validation failed: ${e.message}", e)
            }
        }

        try {
            signatureProfileValidator.validate(signature)
        } catch (e: Exception) {
            throw SamlValidationException("Signature profile validation failed: ${e.message}", e)
        }

        val valid = credentials.any { credential ->
            runCatching { SignatureValidator.validate(signature, credential) }.isSuccess
        }
        samlAssert(valid) { "Signature verification failed with all IdP credentials" }
    }

    /**
     * Verifies a query string signature.
     */
    fun verifyQueryString(
        queryString: String,
        signatureBase64: String,
        signatureAlgorithmUri: String
    ) {
        val signatureAlgorithm = samlRequire(SignatureAlgorithm.from(signatureAlgorithmUri)) {
            "Unsupported signature algorithm."
        }

        samlAssert(allowedSignatureAlgorithms == null || signatureAlgorithm in allowedSignatureAlgorithms) {
            "Signature algorithm not in allowlist."
        }

        val signatureBytes = try {
            Base64.decode(source = signatureBase64)
        } catch (e: Exception) {
            throw SamlValidationException("Invalid Base64 signature", e)
        }

        val signatureIdx = queryString.indexOf("&Signature=")
        val queryStringWithoutSignature = if (signatureIdx >= 0) {
            queryString.substring(0, signatureIdx).toByteArray(Charsets.UTF_8)
        } else {
            throw SamlValidationException("Missing Signature parameter")
        }

        // Try to verify with each credential
        val verified = credentials.any { credential ->
            val publicKey = credential.publicKey ?: return@any false
            runCatching {
                val signature = Signature.getInstance(signatureAlgorithm.jcaAlgorithm)
                signature.initVerify(publicKey)
                signature.update(queryStringWithoutSignature)
                signature.verify(signatureBytes)
            }.getOrDefault(false)
        }

        samlAssert(verified) { "HTTP-Redirect signature verification failed" }
    }
}
