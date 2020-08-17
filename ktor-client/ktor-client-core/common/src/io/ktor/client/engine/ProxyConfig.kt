package io.ktor.client.engine

import io.ktor.http.*
import io.ktor.util.network.*


/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 */
expect class ProxyConfig

/**
 * Type of configured proxy.
 */
expect val ProxyConfig.type: ProxyType

/**
 * Types of proxy
 */
@Suppress("NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "KDocMissingDocumentation")
enum class ProxyType {
    SOCKS,
    HTTP,
    UNKNOWN
}

/**
 * Resolve remote address of [ProxyConfig].
 *
 * This operations can block.
 */
expect fun ProxyConfig.resolveAddress(): NetworkAddress

/**
 * [ProxyConfig] factory.
 */
expect object ProxyBuilder {
    /**
     * Create http proxy from [url].
     */
    fun http(url: Url): ProxyConfig

    /**
     * Create socks proxy from [host] and [port].
     */
    fun socks(host: String, port: Int): ProxyConfig
}

/**
 * Create http proxy from [urlString].
 */
fun ProxyBuilder.http(urlString: String): ProxyConfig = http(Url(urlString))
