package io.ktor.server.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
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

@Deprecated("Use httpServer with CoroutineScope receiver")
fun httpServer(settings: HttpServerSettings, parentJob: Job? = null, handler: HttpRequestHandler): HttpServer {
    val parent = parentJob ?: EmptyCoroutineContext
    val scope = CoroutineScope(GlobalScope.newCoroutineContext(parent))

    return scope.httpServer(settings, handler = handler)
}

@Deprecated("Use httpServer with CoroutineScope receiver")
fun httpServer(settings: HttpServerSettings, parentJob: Job? = null, callDispatcher: CoroutineContext?, handler: HttpRequestHandler): HttpServer {
    if (callDispatcher != null) {
        throw UnsupportedOperationException()
    }

    @Suppress("DEPRECATION")
    return httpServer(settings, parentJob, handler)
}

/**
 * Start an http server with [settings] invoking [handler] for every request
 */
fun CoroutineScope.httpServer(settings: HttpServerSettings,
                              handler: HttpRequestHandler): HttpServer {
    val socket = CompletableDeferred<ServerSocket>()

    val serverLatch = CompletableDeferred<Unit>()
    val serverJob = launch(
        context = CoroutineName("server-root-${settings.port}"),
        start = CoroutineStart.UNDISPATCHED
    ) {
        serverLatch.await()
    }

    val selector = ActorSelectorManager(coroutineContext)
    val timeout = WeakTimeoutQueue(TimeUnit.SECONDS.toMillis(settings.connectionIdleTimeoutSeconds),
            Clock.systemUTC(),
            { TimeoutCancellationException("Connection IDLE") })

    val acceptJob = launch(serverJob + CoroutineName("accept-${settings.port}")) {
        aSocket(selector).tcp().bind(InetSocketAddress(settings.host, settings.port)).use { server ->
            socket.complete(server)

            val connectionScope =
                SupervisedScope("request", CoroutineScope(serverJob + KtorUncaughtExceptionHandler()))

            try {
                while (true) {
                    val client: Socket = server.accept()

                    val clientJob = connectionScope.startConnectionPipeline(
                            input = client.openReadChannel(),
                            output = client.openWriteChannel(),
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
                connectionScope.cancel()
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