package io.ktor.network.sockets

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*

/**
 * Base type for all async sockets
 */
interface ASocket : Closeable, DisposableHandle {
    val socketContext: Deferred<Unit>

    override fun dispose() {
        try {
            close()
        } catch (ignore: Throwable) {
        }
    }
}

val ASocket.isClosed: Boolean get() = socketContext.isCompleted
suspend fun ASocket.awaitClosed() = socketContext.await()

interface AConnectedSocket : AWritable {
    /**
     * Remote socket address. Could throw an exception if the peer is not yet connected or already disconnected.
     */
    val remoteAddress: SocketAddress
}

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

interface AReadable {
    fun attachForReading(channel: ByteChannel): WriterJob
}

interface AWritable {
    fun attachForWriting(channel: ByteChannel): ReaderJob
}

interface ReadWriteSocket : ASocket, AReadable, AWritable

fun AReadable.openReadChannel(): ByteReadChannel = ByteChannel(false).also { attachForReading(it) }
fun AWritable.openWriteChannel(autoFlush: Boolean = false): ByteWriteChannel = ByteChannel(autoFlush).also { attachForWriting(it) }


interface Socket : ReadWriteSocket, ABoundSocket, AConnectedSocket

interface ServerSocket : ASocket, ABoundSocket, Acceptable<Socket>
