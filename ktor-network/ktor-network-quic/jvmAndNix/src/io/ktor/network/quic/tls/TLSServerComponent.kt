/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

internal expect class TLSServerComponent : TLSComponent {
    /**
     * Accepts TLS ClientHello. Starts to install Handshake keys
     *
     * @param originalDcid - value of the DCID in the Initial packet
     * @param cryptoFramePayload - contents of the CRYPTO frame from the Initial packet
     */
    suspend fun acceptInitialHandshake(
        originalDcid: ByteArray,
        cryptoFramePayload: ByteArray,
    )

    /**
     * Finishes TLS handshake, starts the process of installing 1-RTT keys
     *
     * @param cryptoFramePayload - contents of the CRYPTO frame from the Handshake packet
     */
    suspend fun finishHandshake(
        cryptoFramePayload: ByteArray,
    )
}
