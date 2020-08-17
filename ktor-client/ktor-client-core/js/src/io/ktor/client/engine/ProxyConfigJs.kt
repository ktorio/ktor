package io.ktor.client.engine

import io.ktor.http.*
import io.ktor.util.network.*

/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 */
actual class ProxyConfig

/**
 * [ProxyConfig] factory.
 */
actual object ProxyBuilder {
    /**
     * Create http proxy from [url].
     */
    actual fun http(url: Url): ProxyConfig {
        error("Proxy unsupported in js client engine.")
    }

    /**
     * Create socks proxy from [host] and [port].
     */
    actual fun socks(host: String, port: Int): ProxyConfig {
        error("Proxy unsupported in js client engine.")
    }
}

/**
 * Resolve remote address of [ProxyConfig].
 *
 * This operations can block.
 */
public actual fun ProxyConfig.resolveAddress(): NetworkAddress {
    error("Proxy unsupported in js client engine.")
}

/**
 * Type of configured proxy.
 */
public actual val ProxyConfig.type: ProxyType
    get() = error("Proxy unsupported in js client engine.")
