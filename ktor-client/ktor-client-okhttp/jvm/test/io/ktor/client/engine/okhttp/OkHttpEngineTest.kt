/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
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
}
