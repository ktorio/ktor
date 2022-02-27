/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.http.cio.*
import io.ktor.server.application.*
import io.ktor.server.cio.backend.*
import io.ktor.server.cio.internal.*
import io.ktor.server.engine.*
import io.ktor.util.pipeline.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*

/**
 * Engine that based on CIO backend
 */
public class CIOApplicationEngine(
    environment: ApplicationEngineEnvironment,
    configure: Configuration.() -> Unit
) : BaseApplicationEngine(environment) {

    /**
     * CIO-based server configuration
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Number of seconds that the server will keep HTTP IDLE connections open.
         * A connection is IDLE if there are no active requests running.
         */
        public var connectionIdleTimeoutSeconds: Int = 45
    }

    private val configuration: Configuration = Configuration().apply(configure)

    private val engineDispatcher = Dispatchers.IOBridge

    private val userDispatcher = Dispatchers.IOBridge

    private val startupJob: CompletableDeferred<Unit> = CompletableDeferred()
    private val stopRequest: CompletableJob = Job()

    private var serverJob: Job by atomic(Job())

    init {
        serverJob = initServerJob()
        serverJob.invokeOnCompletion { cause ->
            cause?.let { stopRequest.completeExceptionally(cause) }
            cause?.let { startupJob.completeExceptionally(cause) }
            environment.stop()
        }
    }

    override fun start(wait: Boolean): ApplicationEngine {
        serverJob.start()

        runBlocking {
            startupJob.await()

            if (wait) {
                serverJob.join()
            }
        }

        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        shutdownServer(gracePeriodMillis, timeoutMillis)
    }

    private fun shutdownServer(gracePeriodMillis: Long, timeoutMillis: Long) {
        stopRequest.complete()

        runBlocking {
            val result = withTimeoutOrNull(gracePeriodMillis) {
                serverJob.join()
                true
            }

            if (result == null) {
                // timeout
                serverJob.cancel()

                val forceShutdown = withTimeoutOrNull(timeoutMillis - gracePeriodMillis) {
                    serverJob.join()
                    false
                } ?: true

                if (forceShutdown) {
                    environment.stop()
                }
            }
        }
    }

    private fun CoroutineScope.startConnector(host: String, port: Int): HttpServer {
        val settings = HttpServerSettings(
            host = host,
            port = port,
            connectionIdleTimeoutSeconds = configuration.connectionIdleTimeoutSeconds.toLong()
        )

        return httpServer(settings) { request ->
            handleRequest(request)
        }
    }

    private suspend fun ServerRequestScope.handleRequest(request: Request) {
        withContext(userDispatcher) {
            val call = CIOApplicationCall(
                application,
                request,
                input,
                output,
                engineDispatcher,
                userDispatcher,
                upgraded,
                remoteAddress,
                localAddress,
                this@withContext.coroutineContext
            )

            try {
                pipeline.execute(call)
            } catch (error: Throwable) {
                handleFailure(call, error)
            } finally {
                call.release()
            }
        }
    }

    private fun initServerJob(): Job {
        val environment = environment
        val userDispatcher = userDispatcher
        val stopRequest = stopRequest
        val startupJob = startupJob
        val cioConnectors = resolvedConnectors

        return CoroutineScope(
            environment.parentCoroutineContext + engineDispatcher
        ).launch(start = CoroutineStart.LAZY) {
            val connectors = ArrayList<HttpServer>(environment.connectors.size)

            try {
                environment.connectors.forEach { connectorSpec ->
                    if (connectorSpec.type == ConnectorType.HTTPS) {
                        throw UnsupportedOperationException(
                            "CIO Engine does not currently support HTTPS. Please " +
                                "consider using a different engine if you require HTTPS"
                        )
                    }
                }

                withContext(userDispatcher) {
                    environment.start()
                }

                val connectorsAndServers = environment.connectors.map { connectorSpec ->
                    connectorSpec to startConnector(connectorSpec.host, connectorSpec.port)
                }
                connectors.addAll(connectorsAndServers.map { it.second })

                val resolvedConnectors = connectorsAndServers
                    .map { (connector, server) -> connector to server.serverSocket.await() }
                    .map { (connector, socket) -> connector.withPort(socket.localAddress.port) }
                cioConnectors.complete(resolvedConnectors)
            } catch (cause: Throwable) {
                connectors.forEach { it.rootServerJob.cancel() }
                stopRequest.completeExceptionally(cause)
                startupJob.completeExceptionally(cause)
                throw cause
            }

            startupJob.complete(Unit)
            stopRequest.join()

            // stopping
            connectors.forEach { it.acceptJob.cancel() }

            withContext(userDispatcher) {
                environment.monitor.raise(ApplicationStopPreparing, environment)
            }
        }
    }
}
