package io.ktor.client.engine.cio

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.network.tls.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.atomic.*

internal class Endpoint(
    host: String,
    port: Int,
    private val secure: Boolean,
    private val dispatcher: CoroutineDispatcher,
    private val config: CIOEngineConfig,
    private val connectionFactory: ConnectionFactory,
    private val onDone: () -> Unit
) : Closeable {
    private val tasks: Channel<RequestTask> = Channel(Channel.UNLIMITED)
    private val deliveryPoint: Channel<RequestTask> = Channel()
    private val maxEndpointIdleTime = 2 * config.endpoint.connectTimeout

    @Volatile
    private var connectionsHolder: Int = 0

    private val address = InetSocketAddress(host, port)

    private val postman = launch(dispatcher, start = CoroutineStart.LAZY) {
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

    suspend fun execute(request: DefaultHttpRequest): CIOHttpResponse {
        val result = CompletableDeferred<CIOHttpResponse>()
        val task = RequestTask(request, result)
        tasks.offer(task)
        return result.await()
    }

    private suspend fun makePipelineRequest(task: RequestTask) {
        if (deliveryPoint.offer(task)) return

        val connections = Connections.get(this@Endpoint)
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

    private fun makeDedicatedRequest(task: RequestTask) = launch(dispatcher) {
        val connection = connect()
        val input = connection.openReadChannel()
        val output = connection.openWriteChannel()
        val requestTime = Date()

        val (request, response) = task

        fun closeConnection(cause: Throwable?) {
            try {
                output.close(cause)
                connection.close()
                releaseConnection()
            } catch (_: Throwable) {
            }
        }

        try {
            request.write(output)

            val rawResponse = parseResponse(input) ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")

            val status = rawResponse.status
            val contentLength = rawResponse.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
            val transferEncoding = rawResponse.headers[HttpHeaders.TransferEncoding]
            val connectionType = ConnectionOptions.parse(rawResponse.headers[HttpHeaders.Connection])

            val body = when (status) {
                HttpStatusCode.SwitchingProtocols.value -> {
                    val content = request.content as? ClientUpgradeContent
                            ?: error("Invalid content type: UpgradeContent required")

                    launch(dispatcher) {
                        content.pipeTo(output)
                    }.invokeOnCompletion(::closeConnection)

                    input
                }
                else -> {
                    val httpBodyParser = writer(dispatcher, autoFlush = true) {
                        parseHttpBody(contentLength, transferEncoding, connectionType, input, channel)
                    }

                    httpBodyParser.invokeOnCompletion(::closeConnection)
                    httpBodyParser.channel
                }
            }

            response.complete(CIOHttpResponse(request, requestTime, body, rawResponse, pipelined = false))
        } catch (cause: Throwable) {
            response.completeExceptionally(cause)
        }
    }

    private suspend fun createPipeline() {
        val socket = connect()

        val pipeline = ConnectionPipeline(
            dispatcher,
            config.endpoint.keepAliveTime, config.endpoint.pipelineMaxSize,
            socket,
            deliveryPoint
        )

        pipeline.pipelineContext.invokeOnCompletion { releaseConnection() }
    }

    private suspend fun connect(): Socket {
        val retryAttempts = config.endpoint.connectRetryAttempts
        val connectTimeout = config.endpoint.connectTimeout

        Connections.incrementAndGet(this)

        repeat(retryAttempts) {
            val connection = withTimeoutOrNull(connectTimeout) { connectionFactory.connect(address) } ?: return@repeat

            if (!secure) return@connect connection

            with(config.https) {
                return@connect connection.tls(trustManager, randomAlgorithm, cipherSuites, address.hostName, dispatcher)
            }
        }

        throw ConnectException()
    }

    private fun releaseConnection() {
        connectionFactory.release()
        Connections.decrementAndGet(this@Endpoint)
    }

    override fun close() {
        tasks.close()
    }

    init {
        postman.start()
    }

    companion object {
        private val Connections =
            AtomicIntegerFieldUpdater.newUpdater(Endpoint::class.java, Endpoint::connectionsHolder.name)
    }
}

class ConnectException : Exception("Connect timed out")
