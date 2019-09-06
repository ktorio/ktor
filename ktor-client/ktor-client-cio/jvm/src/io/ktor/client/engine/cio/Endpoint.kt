/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.request.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.network.tls.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.net.*
import kotlin.coroutines.*

internal class Endpoint(
    host: String,
    port: Int,
    private val overProxy: Boolean,
    private val secure: Boolean,
    private val config: CIOEngineConfig,
    private val connectionFactory: ConnectionFactory,
    override val coroutineContext: CoroutineContext,
    private val onDone: () -> Unit
) : CoroutineScope, Closeable {
    private val address = InetSocketAddress(host, port)

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

    suspend fun execute(request: HttpRequestData, callContext: CoroutineContext): HttpResponseData =
        suspendCancellableCoroutine { continuation ->
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
            val connection = connect()
            val input = connection.openReadChannel()
            val output = connection.openWriteChannel()
            val requestTime = GMTDate()

            val timeout = config.requestTimeout
            val responseData = if (timeout == 0L) {
                request.write(output, callContext, overProxy)
                readResponse(requestTime, request, input, output, callContext)
            } else {
                withTimeout(timeout) {
                    request.write(output, callContext, overProxy)
                    readResponse(requestTime, request, input, output, callContext)
                }
            }

            callContext[Job]!!.invokeOnCompletion { cause ->
                try {
                    input.cancel(cause)
                    output.close(cause)
                    connection.close()
                    releaseConnection()
                } catch (_: Throwable) {
                }
            }

            response.resume(responseData)
        } catch (cause: Throwable) {
            response.resumeWithException(cause)
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

    private suspend fun connect(): Socket {
        val retryAttempts = config.endpoint.connectRetryAttempts
        val connectTimeout = config.endpoint.connectTimeout

        connections.incrementAndGet()

        try {
            repeat(retryAttempts) {
                val connection = withTimeoutOrNull(connectTimeout) { connectionFactory.connect(address) }
                    ?: return@repeat

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
        throw FailToConnectException()
    }

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
