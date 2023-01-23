/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.streams

import io.ktor.network.quic.sockets.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.channels.*

internal class QUICServer(datagramSocket: BoundDatagramSocket) :
    QUICSocketBase(datagramSocket),
    BoundQUICSocket,
    AReadable by datagramSocket {
    override val outgoing: SendChannel<QUICStream>
        get() = TODO("Not yet implemented")
    override val incoming: ReceiveChannel<QUICStream>
        get() = TODO("Not yet implemented")
}
