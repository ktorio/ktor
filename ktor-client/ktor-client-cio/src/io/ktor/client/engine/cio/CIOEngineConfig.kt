package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.network.tls.*
import io.ktor.util.*
import java.security.*
import javax.net.ssl.*

private val DEFAULT_RANDOM: String =
    SecureRandom().algorithm.takeIf { it != "unknown" } ?: "NativePRNGNonBlocking"

/**
 * Configuration for [CIO] client engine.
 */
class CIOEngineConfig : HttpClientEngineConfig() {
    /**
     * [Endpoint] settings.
     */
    val endpoint: EndpointConfig = EndpointConfig()
    /**
     * [https] settings.
     */
    val https: HttpsConfig = HttpsConfig()

    /**
     * Maximum allowed connections count.
     */
    var maxConnectionsCount: Int = 1000
}

/**
 * [Endpoint] settings.
 */
class EndpointConfig {
    /**
     * Maximum connections  per single route.
     */
    var maxConnectionsPerRoute: Int = 100

    /**
     * Connection keep-alive time in millis.
     */
    var keepAliveTime: Long = 5000

    /**
     * Maximum number of requests per single pipeline
     */
    var pipelineMaxSize: Int = 20

    /**
     * Connect timeout in millis.
     */
    var connectTimeout: Long = 5000

    /**
     * Maximum number of connection attempts.
     */
    var connectRetryAttempts: Int = 5
}

/**
 * Https settings.
 */
class HttpsConfig {
    /**
     * Custom [X509TrustManager] to verify server authority.
     *
     * Use system by default.
     */
    var trustManager: X509TrustManager? = null

    /**
     * Random nonce generation algorithm.
     */
    var randomAlgorithm: String = DEFAULT_RANDOM

    /**
     * List of allowed [CipherSuite]s.
     */
    var cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites
}
