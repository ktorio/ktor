package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * [HttpClientEngine] for writing tests without network.
 */
class MockEngine(
    override val config: MockEngineConfig
) : HttpClientEngine {
    private var invocationCount = 0
    private val _requestsHistory: MutableList<HttpRequest> = mutableListOf()
    private val _responseHistory: MutableList<HttpResponse> = mutableListOf()
    private val contextState = CompletableDeferred<Unit>()

    init {
        check(config.requestHandlers.size > 0) {
            "No request handler provided in [MockEngineConfig], please provide at least one."
        }
    }

    /**
     * History of executed requests.
     */
    val requestHistory: List<HttpRequest> get() = _requestsHistory

    /**
     * History of sent responses.
     */
    val responseHistory: List<HttpResponse> get() = _responseHistory

    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override val coroutineContext: CoroutineContext = dispatcher + contextState

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = data.toRequest(call)

        if (invocationCount >= config.requestHandlers.size) error("Unhandled ${request.url}")
        val handler = config.requestHandlers[invocationCount]

        invocationCount += 1
        if (config.reuseHandlers) {
            invocationCount %= config.requestHandlers.size
        }


        val response = call.handler(request)

        _requestsHistory.add(request)
        _responseHistory.add(response)

        return HttpEngineCall(request, response)
    }

    @Suppress("KDocMissingDocumentation")
    override fun close() {
        contextState.complete(Unit)
    }

    companion object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply(block))

        /**
         * Create [MockEngine] instance with single request handler.
         */
        operator fun invoke(handler: suspend HttpClientCall.(MockHttpRequest) -> HttpResponse): MockEngine =
            MockEngine(MockEngineConfig().apply {
                requestHandlers.add(handler)
            })
    }
}
