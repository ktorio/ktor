package io.ktor.client.engine.mock

import io.ktor.client.engine.*
import io.ktor.client.response.*

class MockEngineConfig : HttpClientEngineConfig() {
    lateinit var check: suspend MockHttpRequest.() -> HttpResponse
}
