package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*

class MockEngineConfig : HttpClientEngineConfig() {
    lateinit var check: suspend (HttpRequest, HttpClientCall) -> HttpResponse
}