/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

internal class Endpoint(
    private val host: String,
    private val port: Int,
    private val proxy: ProxyConfig?,
    private val secure: Boolean,
    private val config: CIOEngineConfig,
    private val connectionFactory: ConnectionFactory,
    override val coroutineContext: CoroutineContext,
    private val onDone: () -> Unit
) : CoroutineScope, Closeable {
    private val lastActivity = atomic(GMTDate())
    private val connections: AtomicInt = atomic(0)
    private val deliveryPoint: Channel<RequestTask> = Channel()
    private val maxEndpointIdleTime: Long = 2 * config.endpoint.connectTimeout

    private val timeout = launch(coroutineContext + CoroutineName("Endpoint timeout($host:$port)")) {
        try {
            while (true) {
                val remaining = (lastActivity.value + maxEndpointIdleTime).timestamp - GMTDate().timestamp
                if (remaining <= 0) {
                    break
                }

                delay(remaining)
            }
        } catch (_: Throwable) {
        } finally {
            deliveryPoint.close()
            onDone()
        }
    }

    suspend fun execute(
        request: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData {
        lastActivity.value = GMTDate()

        if (!config.pipelining || request.requiresDedicatedConnection()) {
            return makeDedicatedRequest(request, callContext)
        }

        val response = CompletableDeferred<HttpResponseData>()
        val task = RequestTask(request, response, callContext)
        try {
            makePipelineRequest(task)
            return response.await()
        } catch (cause: Throwable) {
            task.response.completeExceptionally(cause)
            throw cause
        }
    }

    private suspend fun makePipelineRequest(task: RequestTask) {
        if (deliveryPoint.trySend(task).isSuccess) return

        val connections = connections.value
        if (connections < config.endpoint.maxConnectionsPerRoute) {
            try {
                createPipeline(task.request)
            } catch (cause: Throwable) {
                task.response.completeExceptionally(cause)
                throw cause
            }
        }

        deliveryPoint.send(task)
    }

    @OptIn(InternalAPI::class)
    private suspend fun makeDedicatedRequest(
        request: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData {
        try {
            val connection = connect(request)
            val input = this@Endpoint.mapEngineExceptions(connection.input, request)
            val originOutput = this@Endpoint.mapEngineExceptions(connection.output, request)

            val output = originOutput.handleHalfClosed(
                callContext,
                config.endpoint.allowHalfClose
            )

            callContext[Job]!!.invokeOnCompletion { cause ->
                val originCause = cause?.unwrapCancellationException()
                try {
                    input.cancel(originCause)
                    originOutput.close(originCause)
                    connection.socket.close()
                    releaseConnection()
                } catch (_: Throwable) {
                }
            }

            val timeout = getRequestTimeout(request.getCapabilityOrNull(HttpTimeout))
            setupTimeout(callContext, request, timeout)

            val requestTime = GMTDate()
            writeRequest(request, output, callContext, proxy != null)
            return readResponse(requestTime, request, input, originOutput, callContext)
        } catch (cause: Throwable) {
            throw cause.mapToKtor(request)
        }
    }

    private fun getRequestTimeout(
        configuration: HttpTimeout.HttpTimeoutCapabilityConfiguration?
    ): Long = if (configuration?.requestTimeoutMillis != null) Long.MAX_VALUE else config.requestTimeout

    private suspend fun createPipeline(request: HttpRequestData) {
        val connection = connect(request)

        val pipeline = ConnectionPipeline(
            config.endpoint.keepAliveTime,
            config.endpoint.pipelineMaxSize,
            connection,
            proxy != null,
            deliveryPoint,
            coroutineContext
        )

        pipeline.pipelineContext.invokeOnCompletion { releaseConnection() }
    }

    private suspend fun connect(requestData: HttpRequestData): Connection {
        val connectAttempts = config.endpoint.connectAttempts
        val (connectTimeout, socketTimeout) = retrieveTimeouts(requestData)
        var timeoutFails = 0

        connections.incrementAndGet()

        try {
            repeat(connectAttempts) {
                val address = InetSocketAddress(host, port)

                val connect: suspend CoroutineScope.() -> Socket = {
                    connectionFactory.connect(address) {
                        this.socketTimeout = socketTimeout
                    }
                }

                val socket = when (connectTimeout) {
                    HttpTimeout.INFINITE_TIMEOUT_MS -> connect()
                    else -> {
                        val connection = withTimeoutOrNull(connectTimeout, connect)
                        if (connection == null) {
                            timeoutFails++
                            return@repeat
                        }
                        connection
                    }
                }

                val connection = socket.connection()
                if (!secure) return@connect connection

                try {
                    if (proxy?.type == ProxyType.HTTP) {
                        startTunnel(requestData, connection.output, connection.input)
                    }
                    val tlsSocket = connection.tls(coroutineContext) {
                        takeFrom(config.https)
                        serverName = serverName ?: address.hostname
                    }
                    return tlsSocket.connection()
                } catch (cause: Throwable) {
                    try {
                        socket.close()
                    } catch (_: Throwable) {
                    }

                    connectionFactory.release()
                    throw cause
                }
            }
        } catch (cause: Throwable) {
            connections.decrementAndGet()
            throw cause
        }

        connections.decrementAndGet()

        throw getTimeoutException(connectAttempts, timeoutFails, requestData)
    }

    /**
     * Defines exact type of exception based on [connectAttempts] and [timeoutFails].
     */
    private fun getTimeoutException(
        connectAttempts: Int,
        timeoutFails: Int,
        request: HttpRequestData
    ): Exception = when (timeoutFails) {
        connectAttempts -> ConnectTimeoutException(request)
        else -> FailToConnectException()
    }

    /**
     * Take timeout attributes from [config] and [HttpTimeout.HttpTimeoutCapabilityConfiguration] and returns a pair of
     * connect timeout and socket timeout to be applied.
     */
    private fun retrieveTimeouts(requestData: HttpRequestData): Pair<Long, Long> {
        val default = config.endpoint.connectTimeout to config.endpoint.socketTimeout
        val timeoutAttributes = requestData.getCapabilityOrNull(HttpTimeout)
            ?: return default

        val socketTimeout = timeoutAttributes.socketTimeoutMillis ?: config.endpoint.socketTimeout
        val connectTimeout = timeoutAttributes.connectTimeoutMillis ?: config.endpoint.connectTimeout
        return connectTimeout to socketTimeout
    }

    private fun releaseConnection() {
        connectionFactory.release()
        connections.decrementAndGet()
    }

    override fun close() {
        timeout.cancel()
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun setupTimeout(callContext: CoroutineContext, request: HttpRequestData, timeout: Long) {
    if (timeout == HttpTimeout.INFINITE_TIMEOUT_MS || timeout == 0L) return

    val timeoutJob = GlobalScope.launch {
        delay(timeout)
        callContext.job.cancel("Request is timed out", HttpRequestTimeoutException(request))
    }

    callContext.job.invokeOnCompletion {
        timeoutJob.cancel()
    }
}

@Suppress("KDocMissingDocumentation")
public class FailToConnectException : Exception("Connect timed out or retry attempts exceeded")

internal expect fun Throwable.mapToKtor(request: HttpRequestData): Throwable
