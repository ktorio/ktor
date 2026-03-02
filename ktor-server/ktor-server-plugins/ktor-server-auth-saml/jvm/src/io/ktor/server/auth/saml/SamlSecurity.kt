/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.security.x509.BasicX509Credential
import java.io.File
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Helper object for cryptographic operations used in SAML.
 */
public object SamlCrypto {
    internal fun loadKeyStore(path: String, password: String, type: String): KeyStore {
        return KeyStore.getInstance(type).apply {
            File(path).inputStream().use { fis ->
                load(fis, password.toCharArray())
            }
        }
    }

    /**
     * Loads a signing credential (certificate and private key) from a KeyStore.
     *
     * This function loads a KeyStore from the specified path and extracts the private key
     * and certificate for the given alias. The credential is loaded once and can be reused
     * across different SAML operations.
     *
     * ## Example Usage
     *
     * ```kotlin
     * val signingCredential = SamlCrypto.loadCredential(
     *     keystorePath = "/path/to/keystore.jks",
     *     keystorePassword = "password",
     *     keyAlias = "sp-key",
     *     keyPassword = "password",
     *     keystoreType = "JKS"
     * )
     *
     * val sp = SamlSpMetadata {
     *     spEntityId = "https://sp.example.com"
     *     signingCredential = signingCredential
     * }
     * ```
     *
     * @param keystorePath Path to the KeyStore file
     * @param keystorePassword Password for the KeyStore
     * @param keyAlias Alias of the private key in the KeyStore
     * @param keyPassword Password for the private key
     * @param keystoreType KeyStore type (JKS, PKCS12, etc.). Default: JKS
     * @return A [BasicX509Credential] containing the private key and certificate
     * @throws KeyStoreException If the KeyStore cannot be loaded, or the key or certificate cannot be found
     */
    public fun loadCredential(
        keystorePath: String,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String,
        keystoreType: String = "JKS"
    ): BasicX509Credential {
        val keyStore = loadKeyStore(keystorePath, keystorePassword, keystoreType)
        val key = keyStore.getKey(keyAlias, keyPassword.toCharArray()) as? PrivateKey
            ?: throw KeyStoreException("Key with alias '$keyAlias' not found or is not a PrivateKey")
        val certificateChain = keyStore.getCertificateChain(keyAlias)
            ?: throw KeyStoreException("Certificate chain for alias '$keyAlias' not found")
        val certificate = certificateChain.first() as? X509Certificate
            ?: throw KeyStoreException("First certificate in chain is not an X509Certificate")
        return BasicX509Credential(certificate).also { it.setPrivateKey(key) }
    }
}

/**
 * Checks if the given credential's private key can be used for SAML assertion decryption.
 */
internal val BasicX509Credential.supportsDecryption
    get(): Boolean {
        val keyAlgorithm = privateKey?.algorithm ?: return false
        return keyAlgorithm.equals("RSA", ignoreCase = true)
    }
