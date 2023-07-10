/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import kotlinx.coroutines.channels.*

internal interface ProtocolCommunicationProvider {
    val messageChannel: SendChannel<TLSMessage>

    suspend fun raiseError(error: QUICTransportError)

    fun getTransportParameters(peerParameters: QUICTransportParameters): QUICTransportParameters
}

internal class TLSMessage(
    val message: ByteArray,
    val encryptionLevel: EncryptionLevel,
    val flush: Boolean,
)
