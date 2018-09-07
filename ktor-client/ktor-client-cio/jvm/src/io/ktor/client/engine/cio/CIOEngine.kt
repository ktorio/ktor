package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.network.selector.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class CIOEngine(override val config: CIOEngineConfig) : HttpClientJvmEngine("ktor-cio") {
    private val endpoints = ConcurrentHashMap<String, Endpoint>()
    @UseExperimental(InternalCoroutinesApi::class)
    private val selectorManager by lazy { ActorSelectorManager(dispatcher.blocking(1)) }

    private val connectionFactory = ConnectionFactory(selectorManager, config.maxConnectionsCount)
    private val closed = AtomicBoolean()

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = DefaultHttpRequest(call, data)
        val response = executeRequest(request)

        return HttpEngineCall(request, response)
    }

    private suspend fun executeRequest(request: DefaultHttpRequest): CIOHttpResponse {
        while (true) {
            if (closed.get()) throw ClientClosedException()

            val endpoint = with(request.url) {
                val address = "$host:$port:$protocol"
                endpoints.computeIfAbsent(address) {
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
