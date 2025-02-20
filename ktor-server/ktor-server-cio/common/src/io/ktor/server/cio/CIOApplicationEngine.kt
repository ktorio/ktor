/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.events.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.backend.*
import io.ktor.server.cio.internal.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.concurrent.*

/**
 * Engine that based on CIO backend
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.CIOApplicationEngine)
 */
public class CIOApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    public val configuration: Configuration,
    private val applicationProvider: () -> Application
) : BaseApplicationEngine(environment, monitor, developmentMode) {

    /**
     * CIO-based server configuration
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.CIOApplicationEngine.Configuration)
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Number of seconds that the server will keep HTTP IDLE connections open.
         * A connection is IDLE if there are no active requests running.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.CIOApplicationEngine.Configuration.connectionIdleTimeoutSeconds)
         */
        public var connectionIdleTimeoutSeconds: Int = 45

        /**
         * Allow the server to bind to an address that is already in use
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.CIOApplicationEngine.Configuration.reuseAddress)
         */
        public var reuseAddress: Boolean = false
    }

    private val engineDispatcher = Dispatchers.IOBridge

    private val userDispatcher = Dispatchers.IOBridge

    private val startupJob: CompletableDeferred<Unit> = CompletableDeferred()
    private val stopRequest: CompletableJob = Job()

    // See KT-67440
    @Volatile
    private var serverJob: Job = Job()

    init {
        serverJob = initServerJob()
        serverJob.invokeOnCompletion { cause ->
            cause?.let { stopRequest.completeExceptionally(cause) }
            cause?.let { startupJob.completeExceptionally(cause) }
        }
    }

    override suspend fun startSuspend(wait: Boolean): ApplicationEngine {
        serverJob.start()

        startupJob.await()
        monitor.raiseCatching(ServerReady, environment, environment.log)

        if (wait) {
            serverJob.join()
        }

        return this
    }

    override fun start(wait: Boolean): ApplicationEngine = runBlockingBridge { startSuspend(wait) }

    override suspend fun stopSuspend(gracePeriodMillis: Long, timeoutMillis: Long) {
        stopRequest.complete()

        val result = withTimeoutOrNull(gracePeriodMillis) {
            serverJob.join()
            true
        }

        if (result == null) {
            // timeout
            serverJob.cancel()

            withTimeoutOrNull(timeoutMillis - gracePeriodMillis) {
                serverJob.join()
            }
        }
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long): Unit = runBlockingBridge {
        stopSuspend(gracePeriodMillis, timeoutMillis)
    }

    private fun CoroutineScope.startConnector(host: String, port: Int): HttpServer {
        val settings = HttpServerSettings(
            host = host,
            port = port,
            connectionIdleTimeoutSeconds = configuration.connectionIdleTimeoutSeconds.toLong(),
            reuseAddress = configuration.reuseAddress
        )

        return httpServer(settings) { request ->
            handleRequest(request)
        }
    }

    private suspend fun addHandlerForExpectedHeader(output: ByteWriteChannel, call: CIOApplicationCall) {
        val continueResponse = "HTTP/1.1 100 Continue\r\n"
        val expectHeaderValue = "100-continue"

        val expectedHeaderPhase = PipelinePhase("ExpectedHeaderPhase")
        call.request.pipeline.insertPhaseBefore(ApplicationReceivePipeline.Before, expectedHeaderPhase)
        call.request.pipeline.intercept(expectedHeaderPhase) {
            val request = call.request
            val version = HttpProtocolVersion.parse(request.httpVersion)
            val expectHeader = call.request.headers[HttpHeaders.Expect]?.lowercase()
            val hasBody = hasBody(request)

            if (expectHeader == null || version == HttpProtocolVersion.HTTP_1_0 || !hasBody) {
                return@intercept
            }

            if (expectHeader != expectHeaderValue) {
                call.respond(HttpStatusCode.ExpectationFailed)
            } else {
                output.apply {
                    output.writeStringUtf8(continueResponse)
                    output.flush()
                }
            }
        }
    }

    private fun hasBody(request: CIOApplicationRequest): Boolean {
        val contentLength = request.headers[HttpHeaders.ContentLength]?.toLong()
        val transferEncoding = request.headers[HttpHeaders.TransferEncoding]
        return transferEncoding != null || (contentLength != null && contentLength > 0)
    }

    private suspend fun ServerRequestScope.handleRequest(request: io.ktor.http.cio.Request) {
        withContext(userDispatcher) requestContext@{
            val call = CIOApplicationCall(
                applicationProvider(),
                request,
                input,
                output,
                engineDispatcher,
                userDispatcher,
                upgraded,
                remoteAddress,
                localAddress,
                this@requestContext.coroutineContext
            )

            try {
                addHandlerForExpectedHeader(output, call)
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
        val cioConnectors = resolvedConnectorsDeferred

        return CoroutineScope(
            applicationProvider().parentCoroutineContext + engineDispatcher
        ).launch(start = CoroutineStart.LAZY) {
            val connectors = ArrayList<HttpServer>(configuration.connectors.size)

            try {
                configuration.connectors.forEach { connectorSpec ->
                    if (connectorSpec.type == ConnectorType.HTTPS) {
                        throw UnsupportedOperationException(
                            "CIO Engine does not currently support HTTPS. Please " +
                                "consider using a different engine if you require HTTPS"
                        )
                    }
                }

                val connectorsAndServers = configuration.connectors.map { connectorSpec ->
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
            connectors.forEach {
                it.acceptJob.cancel()
                it.rootServerJob.cancel()
            }

            withContext(userDispatcher) {
                monitor.raise(ApplicationStopPreparing, environment)
            }
        }
    }
}
