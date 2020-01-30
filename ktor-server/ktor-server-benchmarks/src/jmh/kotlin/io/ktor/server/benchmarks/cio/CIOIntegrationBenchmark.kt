/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.benchmarks.cio

import io.ktor.application.*
import io.ktor.server.benchmarks.*
import io.ktor.server.cio.*
import io.ktor.server.cio.backend.*
import io.ktor.server.engine.*

class CIOIntegrationBenchmark : IntegrationBenchmark<CIOApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): CIOApplicationEngine {
        return embeddedServer(CIO, port, module = main)
    }
}

class CIOAsyncIntegrationBenchmark : AsyncIntegrationBenchmark<CIOApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): CIOApplicationEngine {
        return embeddedServer(CIO, port, module = main)
    }
}
