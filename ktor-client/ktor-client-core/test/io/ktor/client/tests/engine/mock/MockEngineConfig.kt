package io.ktor.client.tests.engine.mock

import io.ktor.client.engine.*
import io.ktor.client.request.*

internal class MockEngineConfig : HttpClientEngineConfig() {
    var checks: List<(HttpRequestData) -> Unit> = listOf()
    var response: MockHttpResponseBuilder =
        MockEngine.EMPTY_SUCCESS_RESPONSE
}