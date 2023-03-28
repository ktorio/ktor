/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import net.luminis.tls.handshake.*
import java.io.*

internal actual class TLSServerComponentFactory(
    private val internalFactory: TlsServerEngineFactory,
) {
    actual fun createTLSServerComponent(
        protocolProvider: ProtocolCommunicationProvider,
    ): TLSServerComponent {
        val component = TLSServerComponent(protocolProvider)
        val engine = internalFactory.createServerEngine(component, component)
        component.initEngine(engine)
        return component
    }
}

internal actual fun tlsServerComponentFactory(
    certificatePath: String,
    privateKeyPath: String,
): TLSServerComponentFactory {
    val certFile = File(certificatePath).inputStream()
    val keyFile = File(privateKeyPath).inputStream()

    val internalFactory = TlsServerEngineFactory(certFile, keyFile)

    return TLSServerComponentFactory(internalFactory)
}
