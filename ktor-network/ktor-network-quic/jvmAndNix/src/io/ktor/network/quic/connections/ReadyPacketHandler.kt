/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.tls.*
import io.ktor.utils.io.core.*

internal sealed interface ReadyPacketHandler {
    val destinationConnectionID: ConnectionID

    val sourceConnectionID: ConnectionID

    suspend fun withDatagramBuilder(body: suspend (BytePacketBuilder) -> Unit)

    fun getPacketNumber(encryptionLevel: EncryptionLevel): Long

    fun getLargestAcknowledged(encryptionLevel: EncryptionLevel): Long

    val usedDatagramSize: Int

    interface Initial : ReadyPacketHandler {
        val token: ByteArray
    }

    interface Retry : ReadyPacketHandler {
        val originalDestinationConnectionID: ConnectionID
        val retryToken: ByteArray
    }

    interface OneRTT : ReadyPacketHandler {
        val spinBit: Boolean
        val keyPhase: Boolean
    }

    interface VersionNegotiation : ReadyPacketHandler {
        val supportedVersions: Array<UInt32>
    }
}
