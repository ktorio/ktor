package io.ktor.server.testing.client

import io.ktor.client.engine.*
import io.ktor.server.testing.*

class TestHttpClientConfig : HttpClientEngineConfig() {
    lateinit var app: TestApplicationEngine
}
