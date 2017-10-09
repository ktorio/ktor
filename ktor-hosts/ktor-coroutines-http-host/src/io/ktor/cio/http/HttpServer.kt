package io.ktor.cio.http

import io.ktor.http.cio.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import kotlin.coroutines.experimental.*

fun httpServer(port: Int = 9096, callDispatcher: CoroutineContext = ioCoroutineDispatcher, handler: suspend (request: Request, input: ByteReadChannel, output: ByteWriteChannel) -> Unit): Pair<Job, Deferred<ServerSocket>> {
    val deferred = CompletableDeferred<ServerSocket>()

    val j = launch(ioCoroutineDispatcher) {
        ActorSelectorManager(ioCoroutineDispatcher).use { selector ->
            aSocket(selector).tcp().bind(InetSocketAddress(port)).use { server ->
                deferred.complete(server)

                while (true) {
                    val client = server.accept()
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
            }
        }
    }

    return Pair(j, deferred)
}
