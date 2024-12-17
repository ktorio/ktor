/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.testing.suites.*
import kotlin.system.measureTimeMillis
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class CIOCompressionTest : CompressionTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOContentTest : ContentTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOHttpServerJvmTest : HttpServerJvmTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }

    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class CIOSustainabilityTest : SustainabilityTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOConfigTest : ConfigTestSuite(CIO)

class CIOConnectionTest : ConnectionTestSuite(CIO) {
    @Test
    fun testShutdownGracePeriodWithConnector() {
        val server = embeddedServer(
            factory = CIO,
            environment = applicationEnvironment(),
            configure = {
                shutdownGracePeriod = 10_000
                connector {
                    port = 8787
                }
            },
        ).start(wait = false)

        val time = measureTimeMillis {
            server.stop()
        }

        assertTrue { time < 5_000 }
    }
}

class CIOPluginsTest : ServerPluginsTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
