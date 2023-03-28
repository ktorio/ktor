/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

internal actual class TLSServerComponentFactory {
    actual fun createTLSServerComponent(protocolProvider: ProtocolCommunicationProvider): TLSServerComponent {
        TODO("Not yet implemented")
    }
}

internal actual fun tlsServerComponentFactory(
    certificatePath: String,
    privateKeyPath: String,
): TLSServerComponentFactory {
    TODO("Not yet implemented")
}
