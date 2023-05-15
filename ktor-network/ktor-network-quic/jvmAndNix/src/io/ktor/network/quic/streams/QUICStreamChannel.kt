/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.streams

import io.ktor.network.sockets.*
import kotlinx.coroutines.channels.*

/**
 * A channel for sending QUIC streams
 */
public interface QUICStreamWriteChannel {
    /**
     * Create new QUIC stream.
     */
    public suspend fun createStream(): QUICStream
}

/**
 * A channel for receiving QUIC streams
 */
public interface QUICStreamReadChannel {
    /**
     * Incoming QUIC streams channel
     */
    public val incoming: ReceiveChannel<QUICStream>

    /**
     * Receive a QUIC stream.
     */
    public suspend fun receive(): QUICStream = incoming.receive()
}

/**
 * A channel for sending and receiving QUIC streams
 */
public interface QUICStreamReadWriteChannel : QUICStreamWriteChannel, QUICStreamReadChannel

/**
 * Represents a bound QUIC stream socket
 */
public interface BoundQUICSocket : ASocket, ABoundSocket, AReadable, QUICStreamReadChannel
