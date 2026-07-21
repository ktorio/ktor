/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnixSocketConnectorDescribeTest {

    @Test
    fun `unix socket connector exposes socket path as address description`() {
        val connector = UnixSocketConnectorBuilder().apply {
            socketPath = "/tmp/test.sock"
        }
        assertEquals("unix:///tmp/test.sock", connector.addressDescription)
    }

    @Test
    fun `http connector keeps default null address description`() {
        val connector = EngineConnectorBuilder(ConnectorType.HTTP).apply {
            host = "127.0.0.1"
            port = 8080
        }
        assertNull(connector.addressDescription)
    }
}
