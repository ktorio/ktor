/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*

internal interface ProtocolCommunicationProvider {
    fun sendCryptoFrame(cryptoPayload: ByteArray, inHandshakePacket: Boolean, flush: Boolean = false)

    suspend fun raiseError(error: CryptoHandshakeError_v1)

    fun getTransportParameters(peerParameters: TransportParameters): TransportParameters
}
