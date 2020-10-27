package io.ktor.client.engine

import io.ktor.http.*
import io.ktor.util.network.*
import java.net.*

/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 */
public actual typealias ProxyConfig = Proxy

/**
 * [ProxyConfig] factory.
 */
public actual object ProxyBuilder {
    /**
     * Create http proxy from [url].
     */
    public actual fun http(url: Url): ProxyConfig = Proxy(Proxy.Type.HTTP, InetSocketAddress(url.host, url.port))

    /**
     * Create socks proxy from [host] and [port].
     */
    public actual fun socks(host: String, port: Int): ProxyConfig = Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
}

/**
 * Resolve remote address of [ProxyConfig].
 *
 * This operations can block.
 */
public actual fun ProxyConfig.resolveAddress(): NetworkAddress = address()

/**
 * Type of configured proxy.
 */
public actual val ProxyConfig.type: ProxyType
    get() = when (type()) {
        Proxy.Type.DIRECT -> ProxyType.SOCKS
        Proxy.Type.HTTP -> ProxyType.HTTP
        else -> ProxyType.UNKNOWN
    }
