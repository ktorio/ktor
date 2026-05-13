/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertSame

class OkHttpEngineTest {

    @Test
    fun usesPreconfiguredDispatcher() {
        val dispatcher = Dispatcher()
        val preconfiguredClient = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .build()

        val engine = OkHttpEngine(OkHttpConfig().apply { preconfigured = preconfiguredClient })
        val cacheField = engine.javaClass.getDeclaredField("clientCache").apply { isAccessible = true }
        val clientCache = cacheField.get(engine) as Map<*, *>

        val client = clientCache[null] as OkHttpClient

        assertSame(dispatcher, client.dispatcher)
    }

    @Test
    fun `dns config is applied to OkHttpClient`() {
        val customDns = Dns { _ -> listOf(InetAddress.getByName("127.0.0.1")) }

        val engine = OkHttpEngine(OkHttpConfig().apply { dns = customDns })
        try {
            val cacheField = engine.javaClass.getDeclaredField("clientCache").apply { isAccessible = true }
            val clientCache = cacheField.get(engine) as Map<*, *>

            val client = clientCache[null] as OkHttpClient

            assertSame(customDns, client.dns)
        } finally {
            engine.close()
        }
    }

    @Test
    fun `default dns is preserved when dns config is not set`() {
        val engine = OkHttpEngine(OkHttpConfig())
        try {
            val cacheField = engine.javaClass.getDeclaredField("clientCache").apply { isAccessible = true }
            val clientCache = cacheField.get(engine) as Map<*, *>

            val client = clientCache[null] as OkHttpClient

            assertSame(Dns.SYSTEM, client.dns)
        } finally {
            engine.close()
        }
    }
}
