package io.ktor.client.engine.cio

import io.ktor.client.engine.*


class CIOEngineConfig : HttpClientEngineConfig() {
    val endpointConfig = EndpointConfig()

    var maxConnectionsCount = 1000
}

class EndpointConfig {
    var maxConnectionsPerRoute: Int = 100
    var keepAliveTime: Int = 5000
    var pipelineMaxSize: Int = 20

    var connectTimeout: Int = 5000
    var connectRetryAttempts: Int = 5
}