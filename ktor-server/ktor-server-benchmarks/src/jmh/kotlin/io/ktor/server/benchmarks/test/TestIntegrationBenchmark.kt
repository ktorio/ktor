package io.ktor.server.benchmarks.test

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.benchmarks.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*

class TestIntegrationBenchmark : IntegrationBenchmark<TestApplicationEngine>() {

    override val localhost: String = ""

    override fun createServer(port: Int, main: Application.() -> Unit): TestApplicationEngine {
        return embeddedServer(TestEngine, port, module = main)
    }

    override fun load(url: String) {
        server.handleRequest(HttpMethod.Get, url).apply {
            if (response.status() != HttpStatusCode.OK) {
                throw IllegalStateException("Expected 'HttpStatusCode.OK' but got '${response.status()}'")
            }
            response.byteContent!!
        }
    }
}
