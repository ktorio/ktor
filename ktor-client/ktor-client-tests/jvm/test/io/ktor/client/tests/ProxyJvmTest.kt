/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.body
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.request.get
import kotlin.test.Test
import io.ktor.client.test.base.*
import io.ktor.http.Url
import kotlin.test.assertEquals

class ProxyJvmTest: ClientLoader() {

    @Test
    fun globalProxyProperty() = clientTests(only("CIO")) {
        val proxyUrl = Url(TCP_SERVER)
        System.setProperty("http.proxyHost", proxyUrl.host)
        System.setProperty("http.proxyPort", proxyUrl.port.toString())

        test { client ->
            try {
                val response = client.get("http://google.com").body<String>()
                assertEquals("proxy", response)
            } finally {
                System.clearProperty("http.proxyHost")
                System.clearProperty("http.proxyPort")
            }
        }
    }

    @Test
    fun configuredProxyHasPriorityOverGlobalOne() = clientTests(only("CIO")) {
        System.setProperty("http.proxyHost", "localhost")
        System.setProperty("http.proxyPort", "1")

        config {
            engine {
                proxy = ProxyBuilder.http(Url(TCP_SERVER))
            }
        }

        test { client ->
            try {
                val response = client.get("http://google.com").body<String>()
                assertEquals("proxy", response)
            } finally {
                System.clearProperty("http.proxyHost")
                System.clearProperty("http.proxyPort")
            }
        }
    }
}
