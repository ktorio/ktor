/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.errors

/**
 * Marker interface for all QUIC transport errors across all versions
 */
internal interface QUICTransportError {
    fun toDebugString(): String
}
