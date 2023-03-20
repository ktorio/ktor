/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.connections.TransportParameters
import io.ktor.network.quic.errors.CryptoHandshakeError_v1
import io.ktor.network.quic.frames.FrameWriter

internal actual class TLSServerComponent : TLSComponent {
    actual suspend fun acceptHandshake(
        originalDcid: ByteArray,
        cryptoFramePayload: ByteArray,
    ) {
        TODO("Not yet implemented")
    }

    actual suspend fun finishHandshake(cryptoFramePayload: ByteArray) {
        TODO("Not yet implemented")
    }

    override suspend fun decrypt(
        payload: ByteArray,
        associatedData: ByteArray,
        packetNumber: Long,
        level: EncryptionLevel,
    ): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun encrypt(
        payload: ByteArray,
        associatedData: ByteArray,
        packetNumber: Long,
        level: EncryptionLevel,
    ): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun headerProtectionMask(sample: ByteArray, level: EncryptionLevel, isDecrypting: Boolean): Long {
        TODO("Not yet implemented")
    }
}
