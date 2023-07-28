/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.integration

import kotlinx.coroutines.*

abstract class ServerBaseIntegrationTest {
    protected fun integrationTest(messageSizeBytes: Int) = runBlocking {
        TestQUICServer().use { server ->
            val client = TestKwikClient(server.port)

            client.testConnection(messageSizeBytes)
        }
    }
}
