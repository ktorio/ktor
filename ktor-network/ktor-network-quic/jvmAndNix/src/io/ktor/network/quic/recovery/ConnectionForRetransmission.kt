/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.recovery

import io.ktor.network.quic.connections.QUICConnection

internal interface ConnectionForRetransmission {
    val streamManager: QUICConnection.StreamManager

    fun needToRetransmitMaxData(): Boolean

    fun currentMaxData(): Long

    fun needToRetransmitMaxStreamsBidirectional(): Boolean

    fun currentMaxStreamsBidirectional(): Long

    fun needToRetransmitMaxStreamsUnidirectional(): Boolean

    fun currentMaxStreamsUnidirectional(): Long
}
