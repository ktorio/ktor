package io.ktor.http.cio.tests

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.nio.channels.*
import java.nio.channels.spi.*
import kotlin.coroutines.experimental.*

// this is only suitable for tests, do not use in production
internal fun testHttpServer(port: Int = 9096, ioCoroutineContext: CoroutineContext, callDispatcher: CoroutineContext, handler: suspend (request: Request, input: ByteReadChannel, output: ByteWriteChannel) -> Unit): Pair<Job, Deferred<ServerSocketChannel>> {
    val deferred = CompletableDeferred<ServerSocketChannel>()
    val j = Job()

    // blocking acceptor
    launch(CommonPool) {
        val server = ServerSocketChannel.open()!!
        server.bind(InetSocketAddress(port))
        deferred.complete(server)

        try {
            while (true) {
                val client = server.accept() ?: break
                client(client, ioCoroutineContext, callDispatcher, handler)
            }
        } catch (expected: ClosedChannelException) {
        } finally {
            server.close()
        }
    }

    return Pair(j, deferred)
}

private suspend fun client(socket: SocketChannel, ioCoroutineContext: CoroutineContext, callDispatcher: CoroutineContext, handler: suspend (request: Request, input: ByteReadChannel, output: ByteWriteChannel) -> Unit) {
    val incoming = ByteChannel(true)
    val outgoing = ByteChannel()

    launch(ioCoroutineContext) {
        val buffer = DefaultByteBufferPool.borrow()

        try {
            while (true) {
                buffer.clear()
                val rc = outgoing.readAvailable(buffer)
                if (rc == -1) break

                buffer.flip()
                while (buffer.hasRemaining()) {
                    socket.write(buffer)
                }
            }
        } finally {
            DefaultByteBufferPool.recycle(buffer)
        }
    }

    launch(ioCoroutineContext) {
        val buffer = DefaultByteBufferPool.borrow()

        try {
            while (true) {
                buffer.clear()
                val rc = socket.read(buffer)
                if (rc == -1) break

                buffer.flip()
                incoming.writeFully(buffer)
            }
        } catch (t: Throwable) {
            incoming.close(t)
        } finally {
            incoming.close()
            DefaultByteBufferPool.recycle(buffer)
        }
    }

    launch(ioCoroutineContext) {
        try {
            handleConnectionPipeline(incoming, outgoing, ioCoroutineContext, callDispatcher, handler)
        } catch (io: IOException) {
        } finally {
            incoming.close()
            outgoing.close()
        }
    }
}

private enum class Event {
    READ, WRITE
}