package io.ktor.server.benchmarks.jetty

import io.ktor.application.*
import io.ktor.server.benchmarks.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*

class JettyIntegrationBenchmark : IntegrationBenchmark<JettyApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): JettyApplicationEngine {
        return embeddedServer(Jetty, port, module = main)
    }
}

class JettyAsyncIntegrationBenchmark() : AsyncIntegrationBenchmark<JettyApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): JettyApplicationEngine {
        return embeddedServer(Jetty, port, module = main)
    }
}
