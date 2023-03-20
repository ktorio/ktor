/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*

internal data class ProtocolCommunicationProvider(
    val sendCryptoFrame: (ByteArray) -> Unit,
    val raiseError: (CryptoHandshakeError_v1) -> Nothing,
    val getTransportParameters: (peerParameters: TransportParameters) -> TransportParameters,
)
