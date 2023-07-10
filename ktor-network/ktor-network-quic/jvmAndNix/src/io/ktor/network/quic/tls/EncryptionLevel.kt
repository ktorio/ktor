/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.packets.*

internal enum class EncryptionLevel {
    Initial, Handshake, AppData
}

internal val QUICPacket.encryptionLevel: EncryptionLevel? get() = when (this) {
    is QUICInitialPacket -> EncryptionLevel.Initial
    is QUICHandshakePacket -> EncryptionLevel.Handshake
    is QUICPacket.ShortHeader -> EncryptionLevel.AppData
    else -> null
}
