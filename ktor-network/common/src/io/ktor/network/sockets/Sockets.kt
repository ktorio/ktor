/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*

/**
 * Base type for all async sockets
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.ASocket)
 */
public interface ASocket : Closeable, DisposableHandle {
    /**
     * Represents a socket lifetime, completes at socket closure
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.ASocket.socketContext)
     */
    public val socketContext: Job

    public override fun dispose() {
        try {
            close()
        } catch (ignore: Throwable) {
        }
    }
}

/**
 * Check if the socket is closed
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.isClosed)
 */
public val ASocket.isClosed: Boolean get() = socketContext.isCompleted

/**
 * Await until socket close
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.awaitClosed)
 */
public suspend fun ASocket.awaitClosed() {
    socketContext.join()

    @OptIn(InternalCoroutinesApi::class)
    if (socketContext.isCancelled) throw socketContext.getCancellationException()
}

/**
 * Represent a connected socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.AConnectedSocket)
 */
public interface AConnectedSocket {
    /**
     * Remote socket address. Could throw an exception if the peer is not yet connected or already disconnected.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.AConnectedSocket.remoteAddress)
     */
    public val remoteAddress: SocketAddress
}

/**
 * Represents a bound socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.ABoundSocket)
 */
public interface ABoundSocket {
    /**
     * Local socket address. Could throw an exception if no address bound yet.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.ABoundSocket.localAddress)
     */
    public val localAddress: SocketAddress
}

/**
 * Represents a socket source, for example server socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.Acceptable)
 */
public interface Acceptable<out S : ASocket> : ASocket {
    /**
     * Suspends until a connection is available and returns it or throws if something
     * goes wrong.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.Acceptable.accept)
     *
     * @return accepted socket
     * @throws IOException
     */
    public suspend fun accept(): S
}

/**
 * Represent a readable socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.AReadable)
 */
public interface AReadable {
    /**
     * Attach [channel] for reading so incoming bytes appears in the attached channel.
     * Only one channel could be attached
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.AReadable.attachForReading)
     *
     * @return a job that does supply data
     */

    public fun attachForReading(channel: ByteChannel): WriterJob
}

/**
 * Represents a writable socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.AWritable)
 */
public interface AWritable {
    /**
     * Attach [channel] for writing so bytes written to the attached channel will be transmitted
     * Only one channel could be attached
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.AWritable.attachForWriting)
     *
     * @return a job that does transmit data from the channel
     */

    public fun attachForWriting(channel: ByteChannel): ReaderJob
}

/**
 * Represents both readable and writable socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.ReadWriteSocket)
 */
public interface ReadWriteSocket : ASocket, AReadable, AWritable

/**
 * Open a read channel, could be done only once
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.openReadChannel)
 */

public fun AReadable.openReadChannel(): ByteReadChannel = ByteChannel(false).also { attachForReading(it) }

/**
 * Open a write channel, could be opened only once
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.openWriteChannel)
 *
 * @param autoFlush whether returned channel do flush for every write operation
 */

public fun AWritable.openWriteChannel(autoFlush: Boolean = false): ByteWriteChannel =
    ByteChannel(autoFlush).also { attachForWriting(it) }

/**
 * Represents a connected socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.Socket)
 */
public interface Socket : ReadWriteSocket, ABoundSocket, AConnectedSocket, CoroutineScope

/**
 * Represents a server-bound socket ready for accepting connections
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.ServerSocket)
 */
public interface ServerSocket : ASocket, ABoundSocket, Acceptable<Socket>

/**
 * The port number of the current server.
 *
 * @throws UnsupportedOperationException if the local socket address does not support a port.
 */
public val ServerSocket.port: Int get() = localAddress.port()

public expect class SocketTimeoutException(message: String) : IOException

/**
 * Represents a connected socket with its input and output
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.Connection)
 */
public class Connection(
    public val socket: Socket,
    public val input: ByteReadChannel,
    public val output: ByteWriteChannel
)

/**
 * Opens socket input and output channels and returns connection object
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.connection)
 */
public fun Socket.connection(): Connection = Connection(this, openReadChannel(), openWriteChannel())
