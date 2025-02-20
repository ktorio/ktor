/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import java.security.*
import java.security.cert.*
import javax.net.ssl.*

/**
 * TLS configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.TLSConfig)
 *
 * @property trustManager: Custom [X509TrustManager] to verify server authority. Use system by default.
 * @property random: [SecureRandom] to use in encryption.
 * @property certificates: list of client certificate chains with private keys.
 * @property cipherSuites: list of allowed [CipherSuite]s.
 * @property serverName: custom server name for TLS server name extension.
 */
public actual class TLSConfig(
    public val random: SecureRandom,
    public val certificates: List<CertificateAndKey>,
    public val trustManager: X509TrustManager,
    public val cipherSuites: List<CipherSuite>,
    public val serverName: String?
)

/**
 * Client certificate chain with private key.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.CertificateAndKey)
 *
 * @property certificateChain: client certificate chain.
 * @property key: [PrivateKey] for certificate chain.
 */
public class CertificateAndKey(public val certificateChain: Array<X509Certificate>, public val key: PrivateKey)
