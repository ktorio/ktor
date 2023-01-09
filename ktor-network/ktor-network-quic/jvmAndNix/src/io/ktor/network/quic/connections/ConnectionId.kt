/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

import kotlin.jvm.*

@Suppress("ClassName")
@JvmInline
internal value class ConnectionId_v1(val array: ByteArray) {
    init {
        require(array.size <= 20) {
            "Connection ID in QUIC v1 should not exceed length of 20 bytes"
        }
    }
}
