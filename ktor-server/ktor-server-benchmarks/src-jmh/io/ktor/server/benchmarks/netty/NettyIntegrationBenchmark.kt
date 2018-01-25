package io.ktor.server.benchmarks.netty

import io.ktor.application.*
import io.ktor.server.benchmarks.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

class NettyIntegrationBenchmark : IntegrationBenchmark<NettyApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): NettyApplicationEngine {
        return embeddedServer(Netty, port, module = main)
    }
}

class NettyAsyncIntegrationBenchmark() : AsyncIntegrationBenchmark<NettyApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): NettyApplicationEngine {
        return embeddedServer(Netty, port, module = main)
    }
}