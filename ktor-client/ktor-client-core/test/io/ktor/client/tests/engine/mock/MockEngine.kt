package io.ktor.client.tests.engine.mock

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*


typealias MockHttpResponseBuilder = (HttpClientCall, HttpRequest) -> HttpEngineCall

internal class MockEngine(override val config: MockEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher =
        Unconfined

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        config.checks.forEach {
            it(data)
        }

        return config.response(call, data.toRequest(call))
    }

    override fun close() {}

    companion object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply(block))

        fun check(check: HttpRequestData.() -> Unit) = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = this@Companion.create {
                block()
                checks += check
            }
        }

        fun setResponse(newResponse: MockHttpResponseBuilder) = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = this@Companion.create {
                block()
                response = newResponse
            }
        }

        val EMPTY_SUCCESS_RESPONSE: (HttpClientCall, HttpRequest) -> HttpEngineCall = { call, request ->
            HttpEngineCall(request, MockHttpResponse(call, HttpStatusCode.OK))
        }
    }
}

