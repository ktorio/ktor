/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

internal expect class TLSServerComponentFactory {
    fun createTLSServerComponent(protocolProvider: ProtocolCommunicationProvider): TLSComponent
}

internal expect fun tlsServerComponentFactory(
    certificatePath: String,
    privateKeyPath: String,
): TLSServerComponentFactory
