package io.ktor.client.engine

import io.ktor.http.*
import io.ktor.util.network.*

/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 *
 * @param url: proxy url address.
 */
actual class ProxyConfig(val url: Url) {
    override fun toString(): String = buildString {
        url.apply {
            append(protocol.name)
            append("://")
            if (user != null) {
                append(user!!.encodeURLParameter())
                if (password != null) {
                    append(':')
                    append(password!!.encodeURLParameter())
                }
                append('@')
            }

            append(hostWithPort)
        }
    }
}

/**
 * Resolve remote address of [ProxyConfig].
 *
 * This operations can block.
 */
public actual fun ProxyConfig.resolveAddress(): io.ktor.util.network.NetworkAddress = NetworkAddress(url.host, url.port)

/**
 * [ProxyConfig] factory.
 */
actual object ProxyBuilder {
    /**
     * Create http proxy from [url].
     */
    actual fun http(url: Url): ProxyConfig {
        require(url.protocol.name.equals(URLProtocol.HTTP.name, ignoreCase = true))

        return ProxyConfig(url)
    }

    /**
     * Create socks proxy from [host] and [port].
     */
    actual fun socks(host: String, port: Int): ProxyConfig = ProxyConfig(URLBuilder().apply {
        protocol = URLProtocol.SOCKS

        this.host = host
        this.port = port
    }.build())
}

/**
 * Type of configured proxy.
 */
public actual val ProxyConfig.type: ProxyType
    get() = when (url.protocol) {
        URLProtocol.HTTP,
        URLProtocol.HTTPS -> ProxyType.HTTP
        URLProtocol.SOCKS -> ProxyType.SOCKS
        else -> ProxyType.UNKNOWN
    }
