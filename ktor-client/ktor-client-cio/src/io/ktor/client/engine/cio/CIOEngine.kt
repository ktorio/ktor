package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.content.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class CIOEngine(private val config: CIOEngineConfig) : HttpClientEngine {
    private val dispatcher = config.dispatcher ?: HTTP_CLIENT_DEFAULT_DISPATCHER
    private val endpoints = ConcurrentHashMap<String, Endpoint>()
    private val connectionFactory = ConnectionFactory(config.maxConnectionsCount)
    private val closed = AtomicBoolean()

    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            CIOHttpRequest(call, this, builder.build())

    internal suspend fun executeRequest(request: CIOHttpRequest, content: OutgoingContent): CIOHttpResponse {
        if (closed.get()) throw ClientClosedException()

        val endpoint = with(request.url) {
            val address = "$host:$port"
            endpoints.computeIfAbsent(address) {
                Endpoint(host, port, dispatcher, config.endpointConfig, connectionFactory)
            }
        }

        return try {
            endpoint.execute(request, content)
        } catch (cause: ClosedChannelException) {
            throw ClientClosedException(cause)
        } finally {
            if (closed.get()) {
                endpoint.close()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) throw ClientClosedException()

        endpoints.forEach { (_, endpoint) ->
            endpoint.close()
        }
    }
}

class ClientClosedException(override val cause: Throwable? = null) : IllegalStateException("Client already closed")
