package io.ktor.client.engine.cio

import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.cio.websocket.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.network.tls.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import java.io.*
import java.net.*
import kotlin.coroutines.*

internal class Endpoint(
    host: String,
    port: Int,
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
                val task = withTimeoutOrNull(maxEndpointIdleTime) {
                    tasks.receive()
                }

                if (task == null) {
                    onDone()
                    tasks.close()
                    continue
                }

                try {
                    if (!config.pipelining || task.requiresDedicatedConnection()) {
                        makeDedicatedRequest(task)
                    } else {
                        makePipelineRequest(task)
                    }
                } catch (cause: Throwable) {
                    task.response.completeExceptionally(cause)
                    throw cause
                }
            }
        } catch (_: Throwable) {
        } finally {
            deliveryPoint.close()
        }
    }

    suspend fun execute(request: HttpRequest, callContext: CoroutineContext): HttpResponse {
        val result = CompletableDeferred<HttpResponse>(parent = callContext[Job])
        val task = RequestTask(request, result, callContext)
        tasks.offer(task)
        return result.await()
    }

    private suspend fun makePipelineRequest(task: RequestTask) {
        if (deliveryPoint.offer(task)) return

        val connections = connections.value
        if (connections < config.endpoint.maxConnectionsPerRoute) {
            try {
                createPipeline()
            } catch (cause: Throwable) {
                task.response.completeExceptionally(cause)
                throw cause
            }
        }

        deliveryPoint.send(task)
    }

    private fun makeDedicatedRequest(task: RequestTask): Job = launch {
        val (request, response, callContext) = task
        try {
            val connection = connect()
            val input = connection.openReadChannel()
            val output = connection.openWriteChannel()
            val requestTime = GMTDate()

            fun closeConnection(cause: Throwable? = null) {
                try {
                    input.cancel(cause)
                    output.close(cause)
                    connection.close()
                    releaseConnection()
                } catch (_: Throwable) {
                }
            }

            request.write(output, callContext)

            val rawResponse = parseResponse(input)
                ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")

            val status = rawResponse.status
            val contentLength = rawResponse.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
            val transferEncoding = rawResponse.headers[HttpHeaders.TransferEncoding]
            val connectionType = ConnectionOptions.parse(rawResponse.headers[HttpHeaders.Connection])
            val headers = CIOHeaders(rawResponse.headers)

            callContext[Job]!!.invokeOnCompletion {
                rawResponse.headers.release()
            }

            if (status == HttpStatusCode.SwitchingProtocols.value) {
                val session = RawWebSocket(input, output, masking = true, coroutineContext = callContext)
                response.complete(WebSocketResponse(callContext, requestTime, session))
                return@launch
            }


            val body = when {
                request.method == HttpMethod.Head -> {
                    closeConnection()
                    ByteReadChannel.Empty
                }
                else -> {
                    val httpBodyParser = GlobalScope.writer(callContext, autoFlush = true) {
                        parseHttpBody(contentLength, transferEncoding, connectionType, input, channel)
                    }

                    httpBodyParser.invokeOnCompletion(::closeConnection)
                    httpBodyParser.channel
                }
            }

            val result = CIOHttpResponse(
                request, headers, requestTime, body, rawResponse,
                coroutineContext = callContext
            )

            response.complete(result)
        } catch (cause: Throwable) {
            response.completeExceptionally(cause)
        }
    }

    private suspend fun createPipeline() {
        val socket = connect()

        val pipeline = ConnectionPipeline(
            config.endpoint.keepAliveTime, config.endpoint.pipelineMaxSize,
            socket,
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
        throw ConnectException()
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

@KtorExperimentalAPI
@Suppress("KDocMissingDocumentation")
class ConnectException : Exception("Connect timed out or retry attempts exceeded")
