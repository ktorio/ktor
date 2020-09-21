/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

internal class Endpoint(
    private val host: String,
    private val port: Int,
    private val overProxy: Boolean,
    private val secure: Boolean,
    private val config: CIOEngineConfig,
    private val connectionFactory: ConnectionFactory,
    override val coroutineContext: CoroutineContext,
    private val onDone: () -> Unit
) : CoroutineScope, Closeable {
    private val address = NetworkAddress(host, port)

    private val connections: AtomicInt = atomic(0)
    private val tasks: Channel<RequestTask> = Channel(Channel.UNLIMITED)
    private val deliveryPoint: Channel<RequestTask> = Channel()
    private val maxEndpointIdleTime: Long = 2 * config.endpoint.connectTimeout

    private val postman = launch {
        try {
            while (true) {
                val task = withTimeout(maxEndpointIdleTime) {
                    tasks.receive()
                }

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
        } catch (cause: Throwable) {
        } finally {
            deliveryPoint.close()
            tasks.close()
            onDone()
        }
    }

    public suspend fun execute(
        request: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData {
        val result = CompletableDeferred<HttpResponseData>()
        val task = RequestTask(request, result, callContext)
        tasks.offer(task)
        return result.await()
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
            val input = this@Endpoint.mapEngineExceptions(connection.openReadChannel(), request)
            val originOutput = this@Endpoint.mapEngineExceptions(connection.openWriteChannel(), request)

            val output = originOutput.handleHalfClosed(
                callContext, config.endpoint.allowHalfClose
            )

            callContext[Job]!!.invokeOnCompletion { cause ->
                try {
                    input.cancel(cause)
                    originOutput.close(cause)
                    connection.close()
                    releaseConnection()
                } catch (_: Throwable) {
                }
            }

            val timeout = config.requestTimeout

            val responseData = handleTimeout(timeout) {
                writeRequestAndReadResponse(request, output, callContext, input, originOutput)
            }

            response.complete(responseData)
        } catch (cause: Throwable) {
            response.completeExceptionally(cause.mapToKtor(request))
        }
    }

    private suspend fun writeRequestAndReadResponse(
        request: HttpRequestData, output: ByteWriteChannel, callContext: CoroutineContext,
        input: ByteReadChannel, originOutput: ByteWriteChannel
    ): HttpResponseData {
        val requestTime = GMTDate()
        request.write(output, callContext, overProxy)

        return readResponse(requestTime, request, input, originOutput, callContext)
    }

    private suspend fun createPipeline(request: HttpRequestData) {
        val socket = connect(request)

        val pipeline = ConnectionPipeline(
            config.endpoint.keepAliveTime, config.endpoint.pipelineMaxSize,
            socket,
            overProxy,
            deliveryPoint,
            coroutineContext
        )

        pipeline.pipelineContext.invokeOnCompletion { releaseConnection() }
    }

    private suspend fun connect(requestData: HttpRequestData): Socket {
        val retryAttempts = config.endpoint.connectRetryAttempts
        val (connectTimeout, socketTimeout) = retrieveTimeouts(requestData)
        var timeoutFails = 0

        connections.incrementAndGet()

        try {
            repeat(retryAttempts) {
                val connect: suspend CoroutineScope.() -> Socket = {
                    connectionFactory.connect(address) {
                        this.socketTimeout = socketTimeout
                    }
                }

                val connection = when (connectTimeout) {
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

                if (!secure) return@connect connection

                try {
                    return connection.tls(coroutineContext) {
                        takeFrom(config.https)
                        serverName = serverName ?: address.hostname
                    }
                } catch (cause: Throwable) {
                    try {
                        connection.close()
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

        throw getTimeoutException(retryAttempts, timeoutFails, requestData)
    }

    /**
     * Defines exact type of exception based on [retryAttempts] and [timeoutFails].
     */
    private fun getTimeoutException(retryAttempts: Int, timeoutFails: Int, request: HttpRequestData) =
        when (timeoutFails) {
            retryAttempts -> ConnectTimeoutException(request)
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
        tasks.close()
    }
}

private suspend fun <T> CoroutineScope.handleTimeout(
    timeout: Long, block: suspend CoroutineScope.() -> T
): T = if (timeout == HttpTimeout.INFINITE_TIMEOUT_MS) {
    block()
} else {
    withTimeout(timeout, block)
}


@Suppress("KDocMissingDocumentation")
@Deprecated(
    "Binary compatibility.",
    level = DeprecationLevel.HIDDEN, replaceWith = ReplaceWith("FailToConnectException")
)
public open class ConnectException : Exception("Connect timed out or retry attempts exceeded")

@Suppress("KDocMissingDocumentation")
@KtorExperimentalAPI
public class FailToConnectException : Exception("Connect timed out or retry attempts exceeded")

internal expect fun Throwable.mapToKtor(request: HttpRequestData): Throwable
