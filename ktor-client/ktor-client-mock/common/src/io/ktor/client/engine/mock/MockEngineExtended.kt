package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Extended implementation of MockEngine.
 *
 * Key differences with original implementation:
 *  - responses are added to the engine via enqueueResponse()
 *  - responses are given back in order they were added to the engine
 *  - received requests are saved and can be retrieved via takeRequest()
 *  - stored responses and requests can be reset via reset() thus making the
 *      engine reusable throughout tests
 */
class MockEngineExtended(
    override val config: HttpClientEngineConfig
) : HttpClientEngine {

    private var invocationCount = 0

    private val responses: MutableMap<Int, HttpClientCall.() -> MockHttpResponse> = mutableMapOf()

    private val processedRequests: MutableList<MockHttpRequest> = mutableListOf()

    fun enqueueResponses(vararg mockResponses: HttpClientCall.() -> MockHttpResponse) {
        mockResponses.forEachIndexed { index, function ->
            responses[index] = function
        }
    }

    fun reset() {
        responses.clear()
        processedRequests.clear()
        invocationCount = 0
    }

    fun takeRequest(order: Int = processedRequests.size - 1) = processedRequests[order]

    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override val coroutineContext: CoroutineContext = dispatcher + CompletableDeferred<Unit>()

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = data.toRequest(call)
        if (invocationCount == responses.size) {
            error("Unhandled ${request.url} on invocationCount=$invocationCount")
        } else {
            processedRequests.add(request)
            val response = responses[invocationCount++]!!.invoke(call)
            return HttpEngineCall(request, response)
        }
    }

    override fun close() {}

    companion object : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): MockEngineExtended =
            MockEngineExtended(HttpClientEngineConfig().apply(block))
    }
}
