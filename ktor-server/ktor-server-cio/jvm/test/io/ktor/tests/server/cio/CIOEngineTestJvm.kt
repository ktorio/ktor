/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.server.cio.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

class CIOCompressionTest : CompressionTestSuite<CIOServerEngine, CIOServerEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOContentTest : ContentTestSuite<CIOServerEngine, CIOServerEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOHttpServerJvmTest : HttpServerJvmTestSuite<CIOServerEngine, CIOServerEngine.Configuration>(CIO) {
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

class CIOSustainabilityTest : SustainabilityTestSuite<CIOServerEngine, CIOServerEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOConfigTest : ConfigTestSuite(CIO)

class CIOConnectionTest : ConnectionTestSuite(CIO)

class CIOPluginsTest : ServerPluginsTestSuite<CIOServerEngine, CIOServerEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
