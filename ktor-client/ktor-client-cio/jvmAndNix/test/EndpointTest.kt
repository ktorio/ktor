/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import kotlin.test.*

class EndpointTest {

    @Test
    fun testRequestTimeout() {
        assertEquals(10, getRequestTimeout(requestTimeout(10), CIOEngineConfig()))
        assertEquals(15000, getRequestTimeout(requestTimeout(null), CIOEngineConfig()))
        assertEquals(15000, getRequestTimeout(null, CIOEngineConfig()))
    }

    private fun requestTimeout(timeout: Long?) = HttpTimeout.HttpTimeoutCapabilityConfiguration(
        requestTimeoutMillis = timeout
    )
}
