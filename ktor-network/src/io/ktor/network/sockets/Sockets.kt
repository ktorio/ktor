package io.ktor.network.sockets

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import java.io.*
import java.net.*

/**
 * Base type for all async sockets
 */
interface ASocket : Closeable, DisposableHandle {
    /**
     * Represents a socket lifetime, completes at socket closure
     */
    @KtorExperimentalAPI
    val socketContext: Deferred<Unit>

    override fun dispose() {
        try {
            close()
        } catch (ignore: Throwable) {
        }
    }
}

/**
 * Check if the socket is closed
 */
val ASocket.isClosed: Boolean get() = socketContext.isCompleted

/**
 * Await until socket close
 */
suspend fun ASocket.awaitClosed(): Unit = socketContext.await()

/**
 * Represent a connected socket
 */
interface AConnectedSocket : AWritable {
    /**
     * Remote socket address. Could throw an exception if the peer is not yet connected or already disconnected.
     */
    val remoteAddress: SocketAddress
}

/**
 * Represents a bound socket
 */
interface ABoundSocket {
    /**
     * Local socket address. Could throw an exception if no address bound yet.
     */
    val localAddress: SocketAddress
}

/**
 * Represents a socket source, for example server socket
 */
interface Acceptable<out S : ASocket> : ASocket {
    /**
     * accepts socket connection or suspends if none yet available.
     * @return accepted socket
     */
    suspend fun accept(): S
}

/**
 * Represent a readable socket
 */
interface AReadable {
    /**
     * Attach [channel] for reading so incoming bytes appears in the attached channel.
     * Only one channel could be attached
     * @return a job that does supply data
     */
    @KtorExperimentalAPI
    fun attachForReading(channel: ByteChannel): WriterJob
}

/**
 * Represents a writable socket
 */
interface AWritable {
    /**
     * Attach [channel] for writing so bytes written to the attached channel will be transmitted
     * Only one channel could be attached
     * @return a job that does transmit data from the channel
     */
    @KtorExperimentalAPI
    fun attachForWriting(channel: ByteChannel): ReaderJob
}

/**
 * Represents both readable and writable socket
 */
interface ReadWriteSocket : ASocket, AReadable, AWritable

/**
 * Open a read channel, could be done only once
 */
fun AReadable.openReadChannel(): ByteReadChannel = ByteChannel(false).also { attachForReading(it) }

/**
 * Open a write channel, could be opened only once
 * @param autoFlush whether returned channel do flush for every write operation
 */
fun AWritable.openWriteChannel(autoFlush: Boolean = false): ByteWriteChannel = ByteChannel(autoFlush).also { attachForWriting(it) }

/**
 * Represents a connected socket
 */
interface Socket : ReadWriteSocket, ABoundSocket, AConnectedSocket

/**
 * Represents a server bound socket ready for accepting connections
 */
interface ServerSocket : ASocket, ABoundSocket, Acceptable<Socket>
