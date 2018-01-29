package io.ktor.server.benchmarks

import io.ktor.server.benchmarks.cio.*
import io.ktor.server.benchmarks.jetty.*
import io.ktor.server.benchmarks.netty.*
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
abstract class PlatformBenchmark {
    private val httpClient = OkHttpBenchmarkClient()
    private val port = 5678

    abstract fun runServer(port: Int)
    abstract fun stopServer()

    @Setup
    fun setupServer() {
        runServer(port)
    }

    @TearDown
    fun shutdownServer() {
        stopServer()
    }

    @Setup
    fun configureClient() {
        httpClient.setup()
    }

    @TearDown
    fun shutdownClient() {
        httpClient.shutdown()
    }

    private fun load(url: String) {
        httpClient.load(url)
    }

    @Benchmark
    fun sayOK() {
        load("http://localhost:$port/sayOK")
    }
}

/*
Benchmark                      Mode  Cnt   Score   Error   Units

JettyPlatformBenchmark.sayOK  thrpt   20  42.875 ± 1.089  ops/ms
NettyPlatformBenchmark.sayOK  thrpt   20  61.736 ± 1.792  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 32
        run<NettyPlatformBenchmark>()
        run<JettyPlatformBenchmark>()
        run<CIOPlatformBenchmark>()
    }
}
