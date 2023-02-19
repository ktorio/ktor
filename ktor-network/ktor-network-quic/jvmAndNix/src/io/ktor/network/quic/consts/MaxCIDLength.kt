/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.consts

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.errors.*

/**
 * Object that holds const values of maximum CID length in different QUIC versions
 */
internal object MaxCIDLength {
    // https://www.rfc-editor.org/rfc/rfc9000.html#name-long-header-packets
    private const val V1_MAX_CID_LEN: UInt8 = 20u

    // https://www.rfc-editor.org/rfc/rfc8999.html#name-long-header
    private const val VERSION_NEGOTIATION_MAX_CID_LEN: UInt8 = 255u

    @Suppress("KotlinConstantConditions")
    inline fun fromVersion(version: UInt32, onError: () -> Nothing): UInt8 {
        return when (version) {
            QUICVersion.VersionNegotiation -> VERSION_NEGOTIATION_MAX_CID_LEN
            QUICVersion.V1 -> V1_MAX_CID_LEN
            else -> onError()
        }
    }
}
