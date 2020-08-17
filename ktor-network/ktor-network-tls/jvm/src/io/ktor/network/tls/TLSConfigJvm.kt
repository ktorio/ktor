/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import java.security.*
import java.security.cert.*
import javax.net.ssl.*

/**
 * TLS configuration.
 * @property trustManager: Custom [X509TrustManager] to verify server authority. Use system by default.
 * @property random: [SecureRandom] to use in encryption.
 * @property certificates: list of client certificate chains with private keys.
 * @property cipherSuites: list of allowed [CipherSuite]s.
 * @property serverName: custom server name for TLS server name extension.
 */
actual class TLSConfig(
    val random: SecureRandom,
    val certificates: List<CertificateAndKey>,
    val trustManager: X509TrustManager,
    val cipherSuites: List<CipherSuite>,
    val serverName: String?
)

/**
 * Client certificate chain with private key.
 * @property certificateChain: client certificate chain.
 * @property key: [PrivateKey] for certificate chain.
 */
class CertificateAndKey(val certificateChain: Array<X509Certificate>, val key: PrivateKey)
