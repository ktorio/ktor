/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.backend

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.engine.internal.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Duration.Companion.seconds

private val LOGGER = KtorSimpleLogger("io.ktor.server.cio.backend.UnixSocketServer")

/**
 * Start an http server with [settings] invoking [handler] for every request
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.backend.httpServer)
 */
@OptIn(InternalAPI::class)
public fun CoroutineScope.unixSocketServer(
    settings: UnixSocketServerSettings,
    handler: HttpRequestHandler
): HttpServer {
    val socket = CompletableDeferred<ServerSocket>()

    val serverLatch: CompletableJob = Job()

    val serverJob = launch(
        context = CoroutineName("server-root-$settings"),
        start = CoroutineStart.UNDISPATCHED
    ) {
        serverLatch.join()
    }

    val selector = SelectorManager(coroutineContext)
    val timeout = settings.connectionIdleTimeoutSeconds.seconds

    val acceptJob = launch(serverJob + CoroutineName("accept-$settings")) {
        val socketFile = Path(settings.socketPath)
        if (SystemFileSystem.exists(socketFile)) {
            SystemFileSystem.delete(socketFile)
        }

        val serverSocket = aSocket(selector).tcp().bind(UnixSocketAddress(settings.socketPath))

        serverSocket.use { server ->
            socket.complete(server)

            val exceptionHandler = coroutineContext[CoroutineExceptionHandler]
                ?: DefaultUncaughtExceptionHandler(LOGGER)

            val connectionScope = CoroutineScope(
                coroutineContext +
                    SupervisorJob(serverJob) +
                    exceptionHandler +
                    CoroutineName("request")
            )

            try {
                while (true) {
                    val client: Socket = try {
                        server.accept()
                    } catch (cause: IOException) {
                        LOGGER.trace("Failed to accept connection", cause)
                        continue
                    }

                    val connection = ServerIncomingConnection(
                        client.openReadChannel(),
                        client.openWriteChannel(),
                        client.remoteAddress.toNetworkAddress(),
                        client.localAddress.toNetworkAddress()
                    )

                    val clientJob = connectionScope.startServerConnectionPipeline(
                        connection,
                        timeout,
                        handler
                    )

                    clientJob.invokeOnCompletion {
                        client.close()
                    }
                }
            } catch (closed: ClosedChannelException) {
                LOGGER.trace("Server socket closed", closed)
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
    }

    serverJob.invokeOnCompletion {
        selector.close()
    }

    return HttpServer(serverJob, acceptJob, socket)
}
