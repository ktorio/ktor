package io.ktor.server.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.WeakTimeoutQueue
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.util.*
import kotlinx.coroutines.*
import org.slf4j.*
import java.net.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.*

/**
 * Represents a server instance
 * @property rootServerJob server job - root for all jobs
 * @property acceptJob client connections accepting job
 * @property serverSocket a deferred server socket instance, could be completed with error if it failed to bind
 */
@Suppress("MemberVisibilityCanBePrivate")
@KtorExperimentalAPI
class HttpServer(val rootServerJob: Job, val acceptJob: Job, val serverSocket: Deferred<ServerSocket>)

/**
 * HTTP server connector settings
 * @property host to listen to
 * @property port to listen to
 * @property connectionIdleTimeoutSeconds time to live for IDLE connections
 */
@KtorExperimentalAPI
data class HttpServerSettings(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val connectionIdleTimeoutSeconds: Long = 45
)

@Suppress("KDocMissingDocumentation")
@Deprecated("Use httpServer with CoroutineScope receiver", level = DeprecationLevel.ERROR)
fun httpServer(settings: HttpServerSettings, parentJob: Job? = null, handler: HttpRequestHandler): HttpServer {
    val parent = parentJob ?: Dispatchers.Default
    return CoroutineScope(parent).httpServer(settings, handler = handler)
}

@Suppress("KDocMissingDocumentation")
@Deprecated("Use httpServer with CoroutineScope receiver", level = DeprecationLevel.ERROR)
fun httpServer(
    settings: HttpServerSettings,
    parentJob: Job? = null,
    callDispatcher: CoroutineContext?,
    handler: HttpRequestHandler
): HttpServer {
    if (callDispatcher != null) {
        throw UnsupportedOperationException()
    }

    @Suppress("DEPRECATION_ERROR")
    return httpServer(settings, parentJob, handler)
}

/**
 * Start an http server with [settings] invoking [handler] for every request
 */
@UseExperimental(InternalAPI::class)
fun CoroutineScope.httpServer(
    settings: HttpServerSettings,
    handler: HttpRequestHandler
): HttpServer {
    val socket = CompletableDeferred<ServerSocket>()

    val serverLatch: CompletableJob = Job()
    val serverJob = launch(
        context = CoroutineName("server-root-${settings.port}"),
        start = CoroutineStart.UNDISPATCHED
    ) {
        serverLatch.join()
    }

    val selector = ActorSelectorManager(coroutineContext)
    val timeout = WeakTimeoutQueue(
        TimeUnit.SECONDS.toMillis(settings.connectionIdleTimeoutSeconds)
    )

    val acceptJob = launch(serverJob + CoroutineName("accept-${settings.port}")) {
        aSocket(selector).tcp().bind(InetSocketAddress(settings.host, settings.port)).use { server ->
            socket.complete(server)

            val connectionScope = CoroutineScope(
                coroutineContext +
                    SupervisorJob(serverJob) +
                    KtorUncaughtExceptionHandler() +
                    CoroutineName("request")
            )

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
                coroutineContext.cancel()
            } finally {
                server.close()
                server.awaitClosed()
                connectionScope.coroutineContext.cancel()
            }
        }
    }

    acceptJob.invokeOnCompletion { cause ->
        cause?.let { socket.completeExceptionally(it) }
        serverLatch.complete()
        timeout.process()
    }

    @UseExperimental(InternalCoroutinesApi::class) // TODO it's attach child?
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
    }
}
