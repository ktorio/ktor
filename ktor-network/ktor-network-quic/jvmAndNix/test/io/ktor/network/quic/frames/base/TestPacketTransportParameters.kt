/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames.base

import io.ktor.network.quic.packets.*

data class TestPacketTransportParameters(
    override val ack_delay_exponent: Int = 1,
) : PacketTransportParameters
