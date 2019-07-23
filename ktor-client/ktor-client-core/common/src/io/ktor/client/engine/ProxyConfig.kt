package io.ktor.client.engine

import io.ktor.http.*


/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 */
expect class ProxyConfig

/**
 * [ProxyConfig] factory.
 */
expect object ProxyBuilder {
    /**
     * Create http proxy from url.
     */
    fun http(url: Url): ProxyConfig
}

fun ProxyBuilder.http(urlString: String): ProxyConfig = http(Url(urlString))
