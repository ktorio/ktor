package io.ktor.cio.http

import io.ktor.http.cio.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.nio.channels.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

fun httpServer(port: Int = 9096, callDispatcher: CoroutineContext = ioCoroutineDispatcher, handler: suspend (request: Request, input: ByteReadChannel, output: ByteWriteChannel) -> Unit): Pair<Job, Deferred<ServerSocket>> {
    val deferred = CompletableDeferred<ServerSocket>()

    val j = launch(ioCoroutineDispatcher) {
        ActorSelectorManager(ioCoroutineDispatcher).use { selector ->
            aSocket(selector).tcp().bind(InetSocketAddress(port)).use { server ->
                deferred.complete(server)

                val liveConnections = ConcurrentHashMap<Socket, Unit>()
                try {
                    while (true) {
                        val client = server.accept()
                        liveConnections.put(client, Unit)
                        client.closed.invokeOnCompletion {
                            liveConnections.remove(client)
                        }

                        try {
                            launch(ioCoroutineDispatcher) {
                                try {
                                    handleConnectionPipeline(client.openReadChannel(), client.openWriteChannel(), ioCoroutineDispatcher, callDispatcher, handler)
                                } catch (io: IOException) {
                                } finally {
                                    client.close()
                                }
                            }
                        } catch (rejected: Throwable) {
                            client.close()
                        }
                    }
                } catch (cancelled: CancellationException) {
                } catch (closed: ClosedChannelException) {
                } finally {
                    server.close()
                    server.awaitClosed()
                    val clients = liveConnections.keys.toList()
                    liveConnections.clear()
                    clients.forEach {
                        it.close()
                    }
                    clients.forEach {
                        it.awaitClosed()
                    }
                }
            }
        }
    }

    return Pair(j, deferred)
}
