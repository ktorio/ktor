/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.http.*
import platform.Foundation.*

private const val HTTP_ENABLE_KEY = "HTTPEnable"
private const val HTTP_PROXY_KEY = "HTTPProxy"
private const val HTTP_PORT_KEY = "HTTPPort"
private const val SOCKS_ENABLE_KEY = "SOCKSEnable"
private const val SOCKS_PROXY_KEY = "SOCKSProxy"
private const val SOCKS_PORT_KEY = "SOCKSPort"

internal fun NSURLSessionConfiguration.setupProxy(config: DarwinClientEngineConfig) {
    val proxy = config.proxy ?: return
    val url = proxy.url

    when (url.protocol) {
        URLProtocol.HTTP -> setupHttpProxy(url)
        URLProtocol.HTTPS -> setupHttpProxy(url)
        URLProtocol.SOCKS -> setupSocksProxy(url)
        else -> error("Proxy type ${url.protocol.name} is unsupported by Darwin client engine.")
    }
}

internal fun NSURLSessionConfiguration.setupHttpProxy(url: Url) {
    connectionProxyDictionary = mapOf<Any?, Any?>(
        HTTP_ENABLE_KEY to 1,
        HTTP_PROXY_KEY to url.host,
        HTTP_PORT_KEY to url.port
    )
}

internal fun NSURLSessionConfiguration.setupSocksProxy(url: Url) {
    connectionProxyDictionary = mapOf<Any?, Any?>(
        SOCKS_ENABLE_KEY to 1,
        SOCKS_PROXY_KEY to url.host,
        SOCKS_PORT_KEY to url.port
    )
}
