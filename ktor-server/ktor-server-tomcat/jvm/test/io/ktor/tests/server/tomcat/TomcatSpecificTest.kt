/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.tomcat

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.tomcat.*
import org.apache.catalina.connector.Connector
import org.apache.catalina.core.StandardService
import java.net.BindException
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TomcatSpecificTest {

    @Test
    fun `start fails when the port is already in use`() {
        ServerSocket(0).use { taken ->
            val server = embeddedServer(Tomcat, port = taken.localPort) {}

            try {
                assertFailsWith<BindException> { server.start(wait = false) }
            } finally {
                server.stop(0, 0)
            }
        }
    }

    @Test
    fun `start fails and releases the other connectors when one cannot bind`() {
        val freePort = ServerSocket(0).use { it.localPort }

        ServerSocket(0).use { taken ->
            val server = embeddedServer(Tomcat, configure = {
                connector { port = freePort }
                connector { port = taken.localPort }
            }) {}

            try {
                assertFailsWith<BindException> { server.start(wait = false) }
            } finally {
                server.stop(0, 0)
            }
        }

        // The connector that did bind must be released once start fails.
        ServerSocket(freePort).close()
    }

    @Test
    fun `stop after a failed start still raises ApplicationStopPreparing`() {
        ServerSocket(0).use { taken ->
            val server = embeddedServer(Tomcat, port = taken.localPort) {}
            var stopPreparing = false
            server.monitor.subscribe(ApplicationStopPreparing) { stopPreparing = true }

            assertFailsWith<BindException> { server.start(wait = false) }
            server.stop(0, 0)

            assertTrue(stopPreparing)
        }
    }

    @Test
    fun `start fails when a connector of another service cannot bind`() {
        ServerSocket(0).use { taken ->
            val server = embeddedServer(Tomcat, configure = {
                connector { port = 0 }
                configureTomcat = {
                    server.addService(
                        StandardService().apply {
                            name = "second"
                            addConnector(Connector().apply { port = taken.localPort })
                        }
                    )
                }
            }) {}

            try {
                assertFailsWith<BindException> { server.start(wait = false) }
            } finally {
                server.stop(0, 0)
            }
        }
    }
}
