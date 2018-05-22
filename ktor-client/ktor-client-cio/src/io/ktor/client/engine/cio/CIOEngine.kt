package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class CIOEngine(override val config: CIOEngineConfig) : HttpClientEngine {
    override val dispatcher = config.dispatcher ?: HTTP_CLIENT_DEFAULT_DISPATCHER
    private val endpoints = ConcurrentHashMap<String, Endpoint>()

    private val connectionFactory = ConnectionFactory(config.maxConnectionsCount)
    private val closed = AtomicBoolean()

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = CIOHttpRequest(call, data)
        val response = executeRequest(request)

        return HttpEngineCall(request, response)
    }

    private suspend fun executeRequest(request: CIOHttpRequest): CIOHttpResponse {
        while (true) {
            if (closed.get()) throw ClientClosedException()

            val endpoint = with(request.url) {
                val address = "$host:$port:$protocol"
                endpoints.computeIfAbsent(address) {
                    val secure = (protocol.isSecure())
                    Endpoint(host, port, secure, dispatcher, config, connectionFactory) {
                        endpoints.remove(address)
                    }
                }
            }

            try {
                return endpoint.execute(request)
            } catch (cause: ClosedSendChannelException) {
                if (closed.get()) throw ClientClosedException(cause)
                continue
            } finally {
                if (closed.get()) endpoint.close()
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
