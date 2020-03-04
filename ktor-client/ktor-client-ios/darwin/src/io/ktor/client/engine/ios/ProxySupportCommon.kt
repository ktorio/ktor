/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.http.*
import platform.CFNetwork.*
import platform.CoreFoundation.*
import platform.Foundation.*

internal fun NSURLSessionConfiguration.setupProxy(config: IosClientEngineConfig) {
    val proxy = config.proxy ?: return
    val url = proxy.url

    when (url.protocol) {
        URLProtocol.HTTP -> setupHttpProxy(url)
        URLProtocol.HTTPS -> setupHttpProxy(url)
//        URLProtocol.SOCKS -> setupSocksProxy(url)
        else -> error("Proxy type ${url.protocol.name} is unsupported by iOS client engine.")
    }
}

internal fun NSURLSessionConfiguration.setupHttpProxy(url: Url) {
    connectionProxyDictionary = mapOf(
        kCFNetworkProxiesHTTPEnable.toNSString() to 1,
        kCFNetworkProxiesHTTPProxy.toNSString() to url.host,
        kCFNetworkProxiesHTTPPort.toNSString() to url.port
    )
}

internal fun CFStringRef?.toNSString(): NSString = CFBridgingRelease(this) as NSString
