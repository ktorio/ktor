/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*

/**
 * TLS configuration.
 * @property trustManager Custom [X509TrustManager] to verify server authority. Use system by default.
 * @property random [SecureRandom] to use in encryption.
 * @property certificates list of client certificate chains with private keys.
 * @property cipherSuites list of allowed [CipherSuite]s.
 * @property serverName custom server name for TLS server name extension.
 * @property version version of TLS to use, currently only 1.2 is supported
 * TODO version should be dynamic
 */
public interface TLSConfigJvm {
    public val random: SecureRandom
    public val certificates: List<CertificateAndKey>
    public val trustManager: X509TrustManager
    public val cipherSuites: List<CipherSuite>
    public val serverName: String?
    public val version: TLSVersion
}

/**
 * TLS configuration.
 * @property trustManager Custom [X509TrustManager] to verify server authority. Use system by default.
 * @property random [SecureRandom] to use in encryption.
 * @property certificates list of client certificate chains with private keys.
 * @property cipherSuites list of allowed [CipherSuite]s.
 * @property serverName custom server name for TLS server name extension.
 * @property version version of TLS to use, currently only 1.2 is supported
 * @property role server / client
 */
public actual class TLSConfig(
    override val random: SecureRandom,
    override val certificates: List<CertificateAndKey>,
    override val trustManager: X509TrustManager,
    override val cipherSuites: List<CipherSuite>,
    override val serverName: String?,
    override val version: TLSVersion,
    public val role: NetworkRole,
    public val handshakeTimeoutMillis: Long?,
    public val onHandshake: (Closeable.(TLSHandshakeType, NetworkRole) -> Unit)?,
) : TLSConfigJvm

/**
 * Client certificate chain with private key.
 * @property certificateChain: client certificate chain.
 * @property key: [PrivateKey] for certificate chain.
 */
public class CertificateAndKey(public val certificateChain: Array<X509Certificate>, public val key: PrivateKey)
