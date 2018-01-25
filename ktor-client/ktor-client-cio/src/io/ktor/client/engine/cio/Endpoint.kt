package io.ktor.client.engine.cio

import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.content.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.io.*
import java.net.*
import java.util.concurrent.atomic.*

internal class Endpoint(
        host: String,
        port: Int,
        private val dispatcher: CoroutineDispatcher,
        private val endpointConfig: EndpointConfig,
        private val connectionFactory: ConnectionFactory
) : Closeable {
    private val tasks: Channel<ConnectionRequestTask> = Channel(Channel.UNLIMITED)
    private val deliveryPoint: Channel<ConnectionRequestTask> = Channel()

    @Volatile
    private var connectionsHolder: Int = 0

    private val address = InetSocketAddress(host, port)

    private val postman = launch(dispatcher, start = CoroutineStart.LAZY) {
        try {
            for (task in tasks) {
                if (deliveryPoint.offer(task)) continue

                val connections = Connections.get(this@Endpoint)
                val connect: suspend () -> Boolean = {
                    withTimeoutOrNull(endpointConfig.connectTimeout) { newConnection() } != null
                }

                if (connections < endpointConfig.maxConnectionsPerRoute && !tryExecute(endpointConfig.connectRetryAttempts, connect)) {
                    task.continuation.resumeWithException(ConnectTimeout(task.request))
                    continue
                }

                deliveryPoint.send(task)
            }
        } finally {
            deliveryPoint.close()
        }
    }

    suspend fun execute(request: CIOHttpRequest, content: OutgoingContent): CIOHttpResponse =
            suspendCancellableCoroutine {
                val task = ConnectionRequestTask(request, content, it)
                tasks.offer(task)
            }

    private suspend fun newConnection() {
        Connections.incrementAndGet(this)
        val socket = connectionFactory.connect(address)
        val pipeline = ConnectionPipeline(dispatcher, endpointConfig.keepAliveTime, endpointConfig.pipelineMaxSize, socket, deliveryPoint)

        pipeline.pipelineContext.invokeOnCompletion {
            connectionFactory.release()
            Connections.decrementAndGet(this)
        }
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

class ConnectTimeout(val request: HttpRequest) : Exception("Connect timed out")
