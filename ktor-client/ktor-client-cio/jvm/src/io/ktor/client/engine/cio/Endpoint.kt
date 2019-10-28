/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.network.tls.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*
import java.net.*
import java.nio.channels.*
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
    private val connections: AtomicInt = atomic(0)
    private val tasks: Channel<RequestTask> = Channel(Channel.UNLIMITED)
    private val deliveryPoint: Channel<RequestTask> = Channel()

    private val maxEndpointIdleTime: Long = 2 * config.endpoint.connectTimeout

    private val postman = launch(start = CoroutineStart.LAZY) {
        try {
            while (true) {
                val task = withTimeout(maxEndpointIdleTime) {
                    tasks.receive()
                }

                try {
                    if (!config.pipelining || task.requiresDedicatedConnection()) {
                        makeDedicatedRequest(task)
                    } else {
                        makePipelineRequest(task)
                    }
                } catch (cause: Throwable) {
                    task.response.resumeWithException(cause)
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

    suspend fun execute(
        request: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData = suspendCancellableCoroutine { continuation ->
        val task = RequestTask(request, continuation, callContext)
        tasks.offer(task)
    }

    private suspend fun makePipelineRequest(task: RequestTask) {
        if (deliveryPoint.offer(task)) return

        val connections = connections.value
        if (connections < config.endpoint.maxConnectionsPerRoute) {
            try {
                createPipeline()
            } catch (cause: Throwable) {
                task.response.resumeWithException(cause)
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
            val input = this@Endpoint.mapEngineExceptions(connection.openReadChannel(), task.request)
            val output = this@Endpoint.mapEngineExceptions(connection.openWriteChannel(), task.request)

            val requestTime = GMTDate()

            callContext[Job]!!.invokeOnCompletion { cause ->
                try {
                    input.cancel(cause)
                    output.close(cause)
                    connection.close()
                    releaseConnection()
                } catch (_: Throwable) {
                }
            }

            val timeout = config.requestTimeout
            val writeRequestAndReadResponse: suspend CoroutineScope.() -> HttpResponseData = {
                request.write(output.wrap(callContext, config.endpoint.allowHalfClose), callContext, overProxy)
                readResponse(requestTime, request, input, output, callContext)
            }

            val responseData = if (timeout == HttpTimeout.INFINITE_TIMEOUT_MS) {
                writeRequestAndReadResponse()
            } else {
                withTimeout(timeout, writeRequestAndReadResponse)
            }

            response.resume(responseData)
        } catch (cause: Throwable) {
            val mappedException = when (cause.rootCause) {
                is SocketTimeoutException -> HttpSocketTimeoutException(task.request)
                else -> cause
            }
            response.resumeWithException(mappedException)
        }
    }

    private suspend fun createPipeline() {
        val socket = connect()

        val pipeline = ConnectionPipeline(
            config.endpoint.keepAliveTime, config.endpoint.pipelineMaxSize,
            socket,
            overProxy,
            deliveryPoint,
            coroutineContext
        )

        pipeline.pipelineContext.invokeOnCompletion { releaseConnection() }
    }

    private suspend fun connect(requestData: HttpRequestData? = null): Socket {
        val retryAttempts = config.endpoint.connectRetryAttempts
        val (connectTimeout, socketTimeout) = retrieveTimeouts(requestData)
        var timeoutFails = 0

        connections.incrementAndGet()

        try {
            repeat(retryAttempts) {
                val address = InetSocketAddress(host, port)

                if (address.isUnresolved) throw UnresolvedAddressException()

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
                    with(config.https) {
                        return@connect connection.tls(coroutineContext) {
                            trustManager = this@with.trustManager
                            random = this@with.random
                            cipherSuites = this@with.cipherSuites
                            serverName = this@with.serverName ?: address.hostName
                            certificates += this@with.certificates
                        }
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

        throw getTimeoutException(retryAttempts, timeoutFails, requestData!!)
    }

    /**
     * Defines exact type of exception based on [retryAttempts] and [timeoutFails].
     */
    private fun getTimeoutException(retryAttempts: Int, timeoutFails: Int, request: HttpRequestData) =
        when (timeoutFails) {
            retryAttempts -> HttpConnectTimeoutException(request)
            else -> FailToConnectException()
        }

    /**
     * Take timeout attributes from [config] and [HttpTimeout.HttpTimeoutCapabilityConfiguration] and returns pair of
     * connect timeout and socket timeout to be applied.
     */
    private fun retrieveTimeouts(requestData: HttpRequestData?): Pair<Long, Long> =
        requestData?.getCapabilityOrNull(HttpTimeout)?.let { timeoutAttributes ->
            val socketTimeout = timeoutAttributes.socketTimeoutMillis ?: config.endpoint.socketTimeout
            val connectTimeout = timeoutAttributes.connectTimeoutMillis ?: config.endpoint.connectTimeout
            return connectTimeout to socketTimeout
        } ?: config.endpoint.connectTimeout to config.endpoint.socketTimeout

    private fun releaseConnection() {
        connectionFactory.release()
        connections.decrementAndGet()
    }

    override fun close() {
        tasks.close()
    }

    init {
        postman.start()
    }
}

@Suppress("KDocMissingDocumentation")
@Deprecated(
    "Binary compatibility.",
    level = DeprecationLevel.HIDDEN, replaceWith = ReplaceWith("FailToConnectException")
)
open class ConnectException : Exception("Connect timed out or retry attempts exceeded")

@Suppress("KDocMissingDocumentation")
@KtorExperimentalAPI
class FailToConnectException : Exception("Connect timed out or retry attempts exceeded")
