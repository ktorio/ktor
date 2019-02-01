package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.network.selector.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class CIOEngine(override val config: CIOEngineConfig) : HttpClientJvmEngine("ktor-cio"), WebSocketEngine {
    private val endpoints = ConcurrentHashMap<String, Endpoint>()

    @UseExperimental(InternalCoroutinesApi::class)
    private val selectorManager by lazy { ActorSelectorManager(dispatcher.blocking(1)) }

    private val connectionFactory = ConnectionFactory(selectorManager, config.maxConnectionsCount)
    private val closed = AtomicBoolean()

    override suspend fun execute(
        call: HttpClientCall, data: HttpRequestData
    ): HttpEngineCall = withContext(coroutineContext) {
        val request = DefaultHttpRequest(call, data)
        val response = executeRequest(request)

        return@withContext HttpEngineCall(request, response)
    }

    override suspend fun execute(request: HttpRequest): WebSocketResponse {
        val response = executeRequest(request)
        return response as WebSocketResponse
    }

    private suspend fun executeRequest(request: HttpRequest): HttpResponse {
        while (true) {
            if (closed.get()) throw ClientClosedException()

            val endpoint = with(request.url) {
                val address = "$host:$port:$protocol"
                endpoints.computeIfAbsentWeak(address) {
                    val secure = (protocol.isSecure())
                    Endpoint(
                        host, port, secure,
                        config,
                        connectionFactory, coroutineContext,
                        onDone = { endpoints.remove(address) }
                    )
                }
            }

            val callContext = createCallContext()
            try {
                return endpoint.execute(request, callContext)
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

        coroutineContext[Job]?.invokeOnCompletion {
            selectorManager.close()
        }

        super.close()
    }
}

@Suppress("KDocMissingDocumentation")
class ClientClosedException(override val cause: Throwable? = null) : IllegalStateException("Client already closed")

private fun <K : Any, V : Closeable> ConcurrentHashMap<K, V>.computeIfAbsentWeak(key: K, block: (K) -> V): V {
    get(key)?.let { return it }

    val newValue = block(key)
    val result = putIfAbsent(key, newValue)
    if (result != null) {
        newValue.close()
        return result
    }

    return newValue
}
