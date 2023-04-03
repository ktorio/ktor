/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*

internal object PktConst {
    const val LONG_HEADER_PACKET_TYPE_INITIAL: UInt8 = 0x00u
    const val LONG_HEADER_PACKET_TYPE_0_RTT: UInt8 = 0x10u
    const val LONG_HEADER_PACKET_TYPE_HANDSHAKE: UInt8 = 0x20u
    const val LONG_HEADER_PACKET_TYPE_RETRY: UInt8 = 0x30u

    const val RETRY_PACKET_INTEGRITY_TAG_LENGTH = 16

    /**
     * Length of sample ciphertext used for header protection
     */
    const val HP_SAMPLE_LENGTH = 16

    /**
     * Length of the header that gets appended during encryption
     */
    const val ENCRYPTION_HEADER_LENGTH = 16

    const val SHORT_HEADER_SPIN_BIT: UInt8 = 0x20u
    const val SHORT_HEADER_KEY_PHASE: UInt8 = 0x04u

    const val HEADER_FLAGS_LENGTH = 1
    const val LONG_HEADER_LENGTH_FIELD_MAX_LENGTH = 4
    const val HEADER_PACKET_NUMBER_MAX_LENGTH = 4
    const val LONG_HEADER_VERSION_LENGTH = 4
}
