/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.backend

import io.ktor.util.*
import io.ktor.utils.io.*
import java.net.*

/**
 * Represents a server incoming connection. Usually it is a TCP connection but potentially could be other transport.
 * @property input channel connected to incoming bytes end
 * @property output channel connected to outgoing bytes end
 * @property remoteAddress of the client (optional)
 * @property localAddress on which the client was accepted (optional)
 */
@InternalAPI
class ServerIncomingConnection(
    val input: ByteReadChannel,
    val output: ByteWriteChannel,
    val remoteAddress: SocketAddress?,
    val localAddress: SocketAddress?
) {
    @Deprecated(
        "Specify localAddress as well.",
        level = DeprecationLevel.HIDDEN
    )
    constructor(
        input: ByteReadChannel,
        output: ByteWriteChannel,
        remoteAddress: SocketAddress?
    ) : this(input, output, remoteAddress, null)
}
