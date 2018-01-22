package io.ktor.client.engine.cio

import io.ktor.client.engine.*


class CIOEngineConfig : HttpClientEngineConfig() {
    val endpointConfig = EndpointConfig()
}