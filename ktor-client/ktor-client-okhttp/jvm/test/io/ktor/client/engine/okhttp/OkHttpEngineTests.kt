/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import kotlinx.coroutines.*
import okhttp3.*
import kotlin.test.*

class OkHttpEngineTests {
    @Test
    fun closeTest() {
        val okHttpClient = OkHttpClient()
        val engine = OkHttpEngine(OkHttpConfig().apply { preconfigured = okHttpClient })
        engine.close()

        runBlocking {
            withTimeout(1000) {
                while (!okHttpClient.dispatcher().executorService().isShutdown) {
                    yield()
                }

                assertTrue("OkHttp dispatcher is still working.") { okHttpClient.dispatcher().executorService().isShutdown }
                assertEquals(0, okHttpClient.connectionPool().connectionCount())
                okHttpClient.cache()?.let { assertTrue("OkHttp client cache is not closed.") { it.isClosed } }
            }
        }
    }
}
