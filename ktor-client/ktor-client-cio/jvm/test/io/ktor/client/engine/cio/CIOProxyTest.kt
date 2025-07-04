/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.ProxyBuilder
import io.ktor.http.Url
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CIOProxyTest {

    @Test
    fun androidProxy() {
        try {
            System.setProperty("http.proxyHost", "127.0.0.1")
            System.setProperty("http.proxyPort", "1234")

            val engine = CIOEngine(CIOEngineConfig())
            val proxy = engine.proxy
            assertNotNull(proxy)
            assertEquals(proxy.address(), InetSocketAddress("127.0.0.1", 1234))

        } finally {
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
        }
    }

    @Test
    fun configProxyHasPriorityOverGlobalOne() {
        try {
            System.setProperty("http.proxyHost", "127.0.0.1")
            System.setProperty("http.proxyPort", "1234")

            val engine = CIOEngine(CIOEngineConfig().apply {
                proxy = ProxyBuilder.http(Url("http://127.0.0.2:8888"))
            })

            val proxy = engine.proxy
            assertNotNull(proxy)
            assertEquals(proxy.address(), InetSocketAddress("127.0.0.2", 8888))

        } finally {
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
        }
    }

    @Test
    fun androidProxyNoPort() {
        try {
            System.setProperty("http.proxyHost", "127.0.0.1")

            val engine = CIOEngine(CIOEngineConfig())
            val proxy = engine.proxy
            assertNotNull(proxy)
            assertEquals(proxy.address(), InetSocketAddress("127.0.0.1", 0))
        } finally {
            System.clearProperty("http.proxyHost")
        }
    }

    @Test
    fun noAndroidProxy() {
        val engine = CIOEngine(CIOEngineConfig())
        val proxy = engine.proxy
        assertNull(proxy)
    }
}
