/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

/**
 * TLS Component - is a part of TLS that QUIC uses to do it's job.
 * It only manages TLS Handshake flow and keys and provides them to QUIC
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9001.html)
 */
internal interface TLSComponent {
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
}
