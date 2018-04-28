package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import javax.net.ssl.*

class CIOEngineConfig : HttpClientEngineConfig() {
    val endpoint: EndpointConfig = EndpointConfig()
    val https: HttpsConfig = HttpsConfig()

    var maxConnectionsCount: Int = 1000
}

class EndpointConfig {
    var maxConnectionsPerRoute: Int = 100
    var keepAliveTime: Int = 5000
    var pipelineMaxSize: Int = 20

    var connectTimeout: Int = 5000
    var connectRetryAttempts: Int = 5
}

class HttpsConfig {
    var trustManager: X509TrustManager? = null
    var randomAlgorithm: String = "NativePRNGNonBlocking"
}
