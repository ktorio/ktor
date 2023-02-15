/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.crypto

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.errors.*

internal object Crypto {
    inline fun decryptPacketNumberLength(encrypted: UInt8, onError: (QUICTransportError) -> Nothing): UInt32 {
        TODO("crypto")
    }

    inline fun decryptPacketNumber(encrypted: UInt32, onError: (QUICTransportError) -> Nothing): UInt32 {
        TODO("crypto")
    }
}
