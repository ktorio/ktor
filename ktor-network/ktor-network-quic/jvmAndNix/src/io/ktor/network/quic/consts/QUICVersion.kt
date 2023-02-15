/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.consts

import io.ktor.network.quic.bytes.*

internal object QUICVersion {
    const val VersionNegotiation: UInt32 = 0x00000000u
    const val V1: UInt32 = 0x00000001u
}
