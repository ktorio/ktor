/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

@Suppress("ClassName")
internal enum class PacketType_v1 {
    Initial, ZeroRTT, Handshake, OneRTT, Retry, VersionNegotiation
}
