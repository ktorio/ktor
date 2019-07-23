package io.ktor.client.engine

import io.ktor.http.*
import java.net.*

/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 */
actual typealias ProxyConfig = Proxy

actual object ProxyBuilder {
    actual fun http(url: Url): ProxyConfig = Proxy(Proxy.Type.HTTP, InetSocketAddress(url.host, url.port))
}
