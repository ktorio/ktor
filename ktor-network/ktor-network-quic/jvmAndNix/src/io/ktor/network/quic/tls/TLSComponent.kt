/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.connections.*

internal sealed interface TLSComponent {
    suspend fun decrypt(
        payload: ByteArray,
        associatedData: ByteArray,
        packetNumber: Long,
        level: EncryptionLevel,
    ): ByteArray

    suspend fun encrypt(
        payload: ByteArray,
        associatedData: ByteArray,
        packetNumber: Long,
        level: EncryptionLevel,
    ): ByteArray

    suspend fun headerProtectionMask(
        sample: ByteArray,
        level: EncryptionLevel,
        isDecrypting: Boolean,
    ): Long

    fun onTransportParametersKnown(run: (local: TransportParameters, peer: TransportParameters) -> Unit)
}
