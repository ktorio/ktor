/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.util.*
import java.security.*
import javax.net.ssl.*
import kotlin.coroutines.*

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 */
public actual suspend fun Connection.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket {

    //using old implementation for now if new configuration is not used
    if (config.isClient && config.authentication == null && config.verification == null) {
        return try {
            openTLSSession(socket, input, output, config, coroutineContext)
        } catch (cause: Throwable) {
            input.cancel(cause)
            output.close(cause)
            socket.close()
            throw cause
        }
    }

    //TODO: servername is not validated
    //TODO: client auth doesn't check if certificate is specific to server or client

    val engine = when (val address = socket.remoteAddress) {
        is UnixSocketAddress -> config.sslContext.createSSLEngine()
        is InetSocketAddress -> {
            config.sslContext.createSSLEngine(
                config.serverName ?: address.hostname,
                address.port
            )
        }
    }

    //TODO: we can also set cipherSuites here
    engine.useClientMode = config.isClient

    if (!config.isClient) {
        engine.needClientAuth = config.verification != null
    }

    return SSLEngineSocket(coroutineContext, engine, this)
}

/**
 * Make [Socket] connection secure with TLS.
 */
public suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    trustManager: X509TrustManager? = null,
    randomAlgorithm: String = "NativePRNGNonBlocking",
    cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites,
    serverName: String? = null
): Socket = tls(coroutineContext) {
    this.trustManager = trustManager
    this.random = SecureRandom.getInstance(randomAlgorithm)
    this.cipherSuites = cipherSuites
    this.serverName = serverName
}
