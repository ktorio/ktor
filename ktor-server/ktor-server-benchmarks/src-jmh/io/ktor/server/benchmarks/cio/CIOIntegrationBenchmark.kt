package io.ktor.server.benchmarks.cio

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.server.benchmarks.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

class CIOIntegrationBenchmark : IntegrationBenchmark<CIOApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): CIOApplicationEngine {
        return embeddedServer(CIO, port, module = main)
    }
}

class CIOAsyncIntegrationBenchmark() : AsyncIntegrationBenchmark<CIOApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): CIOApplicationEngine {
        return embeddedServer(CIO, port, module = main)
    }
}
