package io.ktor.client.engine

import io.ktor.http.*
import java.net.*

/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 */
actual typealias ProxyConfig = Proxy

/**
 * [ProxyConfig] factory.
 */
actual object ProxyBuilder {
    /**
     * Create http proxy from [url].
     */
    actual fun http(url: Url): ProxyConfig = Proxy(Proxy.Type.HTTP, InetSocketAddress(url.host, url.port))

    /**
     * Create socks proxy from [host] and [port].
     */
    actual fun socks(host: String, port: Int): ProxyConfig = Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
}
