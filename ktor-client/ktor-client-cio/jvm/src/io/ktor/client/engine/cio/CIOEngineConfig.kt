package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.network.tls.*
import java.security.*
import javax.net.ssl.*

private val DEFAULT_RANDOM: String =
    SecureRandom.getInstanceStrong().algorithm.takeIf { it != "unknown" } ?: "NativePRNGNonBlocking"

class CIOEngineConfig : HttpClientEngineConfig() {
    val endpoint: EndpointConfig = EndpointConfig()
    val https: HttpsConfig = HttpsConfig()

    var maxConnectionsCount: Int = 1000
}

class EndpointConfig {
    var maxConnectionsPerRoute: Int = 100
    var keepAliveTime: Long = 5000
    var pipelineMaxSize: Int = 20

    var connectTimeout: Long = 5000
    var connectRetryAttempts: Int = 5
}

class HttpsConfig {
    var trustManager: X509TrustManager? = null
    var randomAlgorithm: String = DEFAULT_RANDOM
    var cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites
}
