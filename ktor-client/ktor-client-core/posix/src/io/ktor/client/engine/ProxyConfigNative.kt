package io.ktor.client.engine

import io.ktor.http.*

/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 */
actual class ProxyConfig(val url: Url) {
    override fun toString(): String = url.toString()
}

/**
 * [ProxyConfig] factory.
 */
actual object ProxyBuilder {
    /**
     * Create http proxy from url.
     */
    actual fun http(url: Url): ProxyConfig = ProxyConfig(url)
}
