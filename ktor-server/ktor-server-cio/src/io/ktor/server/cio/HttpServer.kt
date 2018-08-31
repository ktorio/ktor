package io.ktor.server.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.util.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.slf4j.*
import java.net.*
import java.nio.channels.*
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.*

class HttpServer(val rootServerJob: Job, val acceptJob: Job, val serverSocket: Deferred<ServerSocket>)

data class HttpServerSettings(
        val host: String = "0.0.0.0",
        val port: Int = 8080,
        val connectionIdleTimeoutSeconds: Long = 45
)

fun httpServer(settings: HttpServerSettings, parentJob: Job? = null, callDispatcher: CoroutineContext? = null, handler: HttpRequestHandler): HttpServer {
    val socket = CompletableDeferred<ServerSocket>()
    val cpuCount = Runtime.getRuntime().availableProcessors()
    val dispatcher = IOCoroutineDispatcher((cpuCount * 2 / 3).coerceAtLeast(2))

    val serverLatch = CompletableDeferred<Unit>()
    val serverJob = launch(dispatcher + CoroutineName("server-root-${settings.port}") + (parentJob ?: EmptyCoroutineContext)) {
        serverLatch.await()
    }

    val selector = ActorSelectorManager(dispatcher)
    val timeout = WeakTimeoutQueue(TimeUnit.SECONDS.toMillis(settings.connectionIdleTimeoutSeconds),
            Clock.systemUTC(),
            { TimeoutCancellationException("Connection IDLE") })

    val acceptJob = launch(dispatcher + serverJob + CoroutineName("accept-${settings.port}")) {
        aSocket(selector).tcp().bind(InetSocketAddress(settings.host, settings.port)).use { server ->
            socket.complete(server)

            try {
                val parentAndHandler = serverJob + KtorUncaughtExceptionHandler()

                while (true) {
                    val client: Socket = server.accept()

                    val clientJob = startConnectionPipeline(
                            input = client.openReadChannel(),
                            output = client.openWriteChannel(),
                            parentJob = parentAndHandler,
                            ioContext = dispatcher,
                            callContext = callDispatcher ?: dispatcher,
                            timeout = timeout,
                            handler = handler
                    )

                    clientJob.invokeOnCompletion {
                        client.close()
                    }
                }
            } catch (closed: ClosedChannelException) {
                coroutineContext.cancel(closed)
            } finally {
                server.close()
                server.awaitClosed()
            }
        }
    }

    acceptJob.invokeOnCompletion { t ->
        t?.let { socket.completeExceptionally(it) }
        serverLatch.complete(Unit)
        timeout.process()
    }

    serverJob.invokeOnCompletion(onCancelling = true) {
        timeout.cancel()
    }
    serverJob.invokeOnCompletion {
        selector.close()
        dispatcher.close()
    }

    return HttpServer(serverJob, acceptJob, socket)
}

private class KtorUncaughtExceptionHandler : CoroutineExceptionHandler {
    private val logger = LoggerFactory.getLogger(KtorUncaughtExceptionHandler::class.java)

    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler.Key

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        if (exception is CancellationException) return

        logger.error(exception)
        // unlike the default coroutine exception handler we shouldn't cancel parent job here
        // otherwise single call failure will cancel the whole server
    }
}