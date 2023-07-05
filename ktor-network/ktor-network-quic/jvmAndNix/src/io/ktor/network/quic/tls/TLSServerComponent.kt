/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("CanSealedSubClassBeObject")

package io.ktor.network.quic.tls

import io.ktor.network.quic.connections.*

internal expect class TLSServerComponent : TLSComponent {
    /**
     * Accepts original destination connection id and uses it to calculate initial keys
     */
    suspend fun acceptOriginalDcid(originalDcid: QUICConnectionID)

    /**
     * Accepts TLS ClientHello. Starts to install Handshake keys
     *
     * @param cryptoFramePayload - contents of the CRYPTO frame from the Initial packet
     */
    suspend fun acceptInitialHandshake(cryptoFramePayload: ByteArray)

    /**
     * Finishes TLS handshake, starts the process of installing 1-RTT keys
     *
     * @param cryptoFramePayload - contents of the CRYPTO frame from the Handshake packet
     */
    suspend fun finishHandshake(
        cryptoFramePayload: ByteArray,
    )
}
