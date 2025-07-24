/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.ProxyConfig
import io.ktor.http.Url
import io.ktor.http.toURI
import java.net.Proxy
import java.net.ProxySelector

internal actual fun lookupGlobalProxy(url: Url): ProxyConfig? {
    val proxies = ProxySelector.getDefault().select(url.toURI())

    return if (proxies.isNotEmpty()) {
        val proxy = proxies.first()

        if (proxies.size == 1 && proxy.type() == Proxy.Type.DIRECT) {
            null
        } else {
            proxies.first()
        }
    } else {
        null
    }
}
