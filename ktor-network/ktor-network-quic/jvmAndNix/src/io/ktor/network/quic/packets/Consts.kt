/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*

internal object Consts {
    const val LONG_HEADER_PACKET_TYPE_INITIAL: UInt8 = 0x00u
    const val LONG_HEADER_PACKET_TYPE_0_RTT: UInt8 = 0x10u
    const val LONG_HEADER_PACKET_TYPE_HANDSHAKE: UInt8 = 0x20u
    const val LONG_HEADER_PACKET_TYPE_RETRY: UInt8 = 0x30u

    const val SHORT_HEADER_SPIN_BIT: UInt8 = 0x20u
    const val SHORT_HEADER_KEY_PHASE: UInt8 = 0x04u
}
