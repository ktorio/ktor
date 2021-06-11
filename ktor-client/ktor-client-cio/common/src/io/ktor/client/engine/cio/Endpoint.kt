/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Connection
import io.ktor.network.tls.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.*
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
        } catch (cause: Throwable) {
        } finally {
            deliveryPoint.close()
            onDone()
        }
    }

    private suspend fun processTask(task: RequestTask) {
        try {
            if (!config.pipelining || task.request.requiresDedicatedConnection()) {
                makeDedicatedRequest(task)
            } else {
                makePipelineRequest(task)
            }
        } catch (cause: Throwable) {
            task.response.completeExceptionally(cause)
            throw cause
        }
    }

    suspend fun execute(
        request: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData {
        lastActivity.value = GMTDate()
        val response = CompletableDeferred<HttpResponseData>()
        val task = RequestTask(request, response, callContext)
        processTask(task)
        return response.await()
    }

    private suspend fun makePipelineRequest(task: RequestTask) {
        if (deliveryPoint.offer(task)) return

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

    private fun makeDedicatedRequest(
        task: RequestTask
    ): Job = launch(task.context + CoroutineName("DedicatedRequest")) {
        val (request, response, callContext) = task
        try {
            val connection = connect(request)
            val input = this@Endpoint.mapEngineExceptions(connection.input, request)
            val originOutput = this@Endpoint.mapEngineExceptions(connection.output, request)

            val output = originOutput.handleHalfClosed(
                callContext,
                config.endpoint.allowHalfClose
            )

            callContext[Job]!!.invokeOnCompletion { cause ->
                try {
                    input.cancel(cause)
                    originOutput.close(cause)
                    connection.socket.close()
                    releaseConnection()
                } catch (_: Throwable) {
                }
            }

            val timeout = getRequestTimeout(request.getCapabilityOrNull(HttpTimeout))

            val responseData = handleTimeout(timeout) {
                writeRequestAndReadResponse(request, output, callContext, input, originOutput)
            }

            response.complete(responseData)
        } catch (cause: Throwable) {
            response.completeExceptionally(cause.mapToKtor(request))
        }
    }

    private fun getRequestTimeout(configuration: HttpTimeout.HttpTimeoutCapabilityConfiguration?): Long {
        return if (configuration?.requestTimeoutMillis != null) {
            configuration.requestTimeoutMillis as Long
        } else {
            config.requestTimeout
        }
    }

    private suspend fun writeRequestAndReadResponse(
        request: HttpRequestData,
        output: ByteWriteChannel,
        callContext: CoroutineContext,
        input: ByteReadChannel,
        originOutput: ByteWriteChannel
    ): HttpResponseData {
        val requestTime = GMTDate()
        request.write(output, callContext, proxy != null)

        return readResponse(requestTime, request, input, originOutput, callContext)
    }

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
                val address = NetworkAddress(host, port)

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
                if (!secure) {
                    return@connect connection
                }

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
    private fun getTimeoutException(connectAttempts: Int, timeoutFails: Int, request: HttpRequestData) =
        when (timeoutFails) {
            connectAttempts -> ConnectTimeoutException(request)
            else -> FailToConnectException()
        }

    /**
     * Take timeout attributes from [config] and [HttpTimeout.HttpTimeoutCapabilityConfiguration] and returns pair of
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

private suspend fun <T> CoroutineScope.handleTimeout(
    timeout: Long,
    block: suspend CoroutineScope.() -> T
): T = if (timeout == HttpTimeout.INFINITE_TIMEOUT_MS) {
    block()
} else {
    withTimeout(timeout, block)
}

@Suppress("KDocMissingDocumentation")
@Deprecated(
    "Binary compatibility.",
    level = DeprecationLevel.HIDDEN,
    replaceWith = ReplaceWith("FailToConnectException")
)
public open class ConnectException : Exception("Connect timed out or retry attempts exceeded")

@Suppress("KDocMissingDocumentation")
public class FailToConnectException : Exception("Connect timed out or retry attempts exceeded")

internal expect fun Throwable.mapToKtor(request: HttpRequestData): Throwable
