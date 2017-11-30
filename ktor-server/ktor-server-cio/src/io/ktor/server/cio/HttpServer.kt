package io.ktor.server.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import java.net.*
import java.nio.channels.*
import java.time.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

class HttpServer(val rootServerJob: Job, val acceptJob: Job, val serverSocket: Deferred<ServerSocket>) {
}

data class HttpServerSettings(
        val host: String = "0.0.0.0",
        val port: Int = 8080,
        val connectionIdleTimeoutSeconds: Long = 45
)

fun httpServer(settings: HttpServerSettings, callDispatcher: CoroutineContext = ioCoroutineDispatcher, handler: HttpRequestHandler): HttpServer {
    val socket = CompletableDeferred<ServerSocket>()

    val serverLatch = CompletableDeferred<Unit>()
    val serverJob = launch(ioCoroutineDispatcher + CoroutineName("server-root-${settings.port}")) {
        serverLatch.await()
    }

    val selector = ActorSelectorManager(ioCoroutineDispatcher)
    val timeout = WeakTimeoutQueue(TimeUnit.SECONDS.toMillis(settings.connectionIdleTimeoutSeconds),
            Clock.systemUTC(),
            { TimeoutCancellationException("Connection IDLE") })

    val acceptJob = launch(ioCoroutineDispatcher + serverJob + CoroutineName("accept-${settings.port}")) {
        aSocket(selector).tcp().bind(InetSocketAddress(settings.host, settings.port)).use { server ->
            socket.complete(server)

            try {
                while (true) {
                    val client: Socket = server.accept()

                    val clientJob = startConnectionPipeline(
                            input = client.openReadChannel(),
                            output = client.openWriteChannel(),
                            ioContext = ioCoroutineDispatcher,
                            callContext = callDispatcher,
                            timeout = timeout,
                            handler = handler
                    )

                    clientJob.invokeOnCompletion {
                        client.close()
                    }

                    serverJob.attachChild(clientJob)
                }
            } catch (closed: ClosedChannelException) {
                coroutineContext.cancel(closed)
            } finally {
                server.close()
                server.awaitClosed()
            }
        }
    }

    acceptJob.invokeOnCompletion {
        serverLatch.complete(Unit)
    }

    serverJob.invokeOnCompletion(onCancelling = true) {
        timeout.cancel()
    }
    serverJob.invokeOnCompletion {
        selector.close()
    }

    return HttpServer(serverJob, acceptJob, socket)
}
