package io.ktor.client.engine

import io.ktor.http.*
import io.ktor.util.network.*


/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 */
public expect class ProxyConfig

/**
 * Type of configured proxy.
 */
public expect val ProxyConfig.type: ProxyType

/**
 * Types of proxy
 */
@Suppress("NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "KDocMissingDocumentation")
public enum class ProxyType {
    SOCKS,
    HTTP,
    UNKNOWN
}

/**
 * Resolve remote address of [ProxyConfig].
 *
 * This operations can block.
 */
public expect fun ProxyConfig.resolveAddress(): NetworkAddress

/**
 * [ProxyConfig] factory.
 */
public expect object ProxyBuilder {
    /**
     * Create http proxy from [url].
     */
    public fun http(url: Url): ProxyConfig

    /**
     * Create socks proxy from [host] and [port].
     */
    public fun socks(host: String, port: Int): ProxyConfig
}

/**
 * Create http proxy from [urlString].
 */
public fun ProxyBuilder.http(urlString: String): ProxyConfig = http(Url(urlString))
