/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.ProxyConfig
import io.ktor.http.Url
import io.ktor.http.toURI
import java.net.Proxy
import java.net.ProxySelector
import java.net.URISyntaxException

internal actual fun lookupGlobalProxy(url: Url): ProxyConfig? {
    val url = try {
        url.toURI()
    } catch (_: URISyntaxException) {
        null
    }

    if (url == null) {
        return null
    }

    val proxies = ProxySelector.getDefault().select(url)

    return if (proxies.isNotEmpty()) {
        val proxy = proxies.first()

        // When no proxy is available, the list will contain one element with type DIRECT
        if (proxies.size == 1 && proxy.type() == Proxy.Type.DIRECT) {
            null
        } else {
            proxy
        }
    } else {
        null
    }
}
