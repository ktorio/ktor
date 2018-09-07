package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.ByteChannel
import java.net.*
import java.nio.channels.*
import java.util.concurrent.*
import kotlin.coroutines.*

// this is only suitable for tests, do not use in production
internal fun testHttpServer(port: Int = 9096, ioCoroutineContext: CoroutineContext, callDispatcher: CoroutineContext, handler: HttpRequestHandler): Pair<Job, Deferred<ServerSocketChannel>> {
    val deferred = CompletableDeferred<ServerSocketChannel>()
    val j = Job()

    // blocking acceptor
    GlobalScope.launch(ioCoroutineContext) {
        val server = ServerSocketChannel.open()!!
        server.bind(InetSocketAddress(port))
        deferred.complete(server)

        val live = ConcurrentHashMap<SocketChannel, Unit>()

        try {
            while (true) {
                val client = server.accept() ?: break
                live.put(client, Unit)
                client(client, ioCoroutineContext, callDispatcher, handler)
            }
        } catch (expected: ClosedChannelException) {
        } finally {
            server.close()
            val clients = live.keys.toList()
            live.keys.clear()
            clients.forEach {
                it.close()
            }
        }
    }

    j.invokeOnCompletion {
        deferred.invokeOnCompletion { t ->
            if (t == null) {
                @UseExperimental(ExperimentalCoroutinesApi::class)
                deferred.getCompleted().close()
            }
        }
    }

    return Pair(j, deferred)
}

private suspend fun client(socket: SocketChannel, ioCoroutineContext: CoroutineContext, callDispatcher: CoroutineContext, handler: HttpRequestHandler) {
    val incoming = ByteChannel(true)
    val outgoing = ByteChannel()

    GlobalScope.launch(ioCoroutineContext) {
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

    GlobalScope.launch(ioCoroutineContext) {
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
            outgoing.close()
            DefaultByteBufferPool.recycle(buffer)
        }
    }

    val timeouts = WeakTimeoutQueue(TimeUnit.HOURS.toMillis(1000))
    @Suppress("DEPRECATION")
    startConnectionPipeline(incoming, outgoing, null, ioCoroutineContext, callDispatcher, timeouts, handler).invokeOnCompletion {
        incoming.close()
        outgoing.close()
    }
}
