/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.http.*
import platform.CFNetwork.*
import platform.Foundation.*

internal actual fun NSURLSessionConfiguration.setupProxy(config: IosClientEngineConfig) {
    val proxy = config.proxy ?: return
    val url = proxy.url

    val type = when (url.protocol) {
        URLProtocol.HTTP -> kCFProxyTypeHTTP
        URLProtocol.HTTPS -> kCFProxyTypeHTTPS
        URLProtocol.SOCKS -> kCFProxyTypeSOCKS
        else -> error("Proxy type ${url.protocol.name} is unsupported by iOS client engine.")
    }

    val port = url.port.toString()
    connectionProxyDictionary = mapOf(
        kCFProxyHostNameKey to url.host,
        kCFProxyPortNumberKey to port,
        kCFProxyTypeKey to type
    )
}
