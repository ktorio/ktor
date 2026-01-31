/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: JUnit5 parameterized tests for throughput benchmarks.
// ABOUTME: Tests all combinations of server engines (Netty, CIO, Jetty, Tomcat) and client engines.

package io.ktor.benchmark.throughput

import io.ktor.client.engine.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.engine.jetty.*
import io.ktor.client.engine.okhttp.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import io.ktor.server.tomcat.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class ThroughputBenchmarkTest {

    @ParameterizedTest(name = "download: {0} -> {1}")
    @MethodSource("engineCombinations")
    fun downloadThroughput(
        serverName: String,
        clientName: String,
        serverFactory: ApplicationEngineFactory<*, *>,
        clientFactory: HttpClientEngineFactory<*>
    ) {
        runBenchmark(serverFactory, clientFactory, BenchmarkScenario.DOWNLOAD, serverName, clientName)
    }

    @ParameterizedTest(name = "upload: {0} -> {1}")
    @MethodSource("engineCombinations")
    fun uploadThroughput(
        serverName: String,
        clientName: String,
        serverFactory: ApplicationEngineFactory<*, *>,
        clientFactory: HttpClientEngineFactory<*>
    ) {
        runBenchmark(serverFactory, clientFactory, BenchmarkScenario.UPLOAD, serverName, clientName)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <
        TServerEngine : ApplicationEngine,
        TServerConfig : ApplicationEngine.Configuration,
        TClientConfig : HttpClientEngineConfig
        > runBenchmark(
        serverFactory: ApplicationEngineFactory<TServerEngine, TServerConfig>,
        clientFactory: HttpClientEngineFactory<TClientConfig>,
        scenario: BenchmarkScenario,
        serverName: String,
        clientName: String
    ) {
        val benchmark = ThroughputBenchmark()

        val result = runBlocking {
            benchmark.run(
                serverEngineFactory = serverFactory,
                clientEngineFactory = clientFactory,
                scenario = scenario
            )
        }

        println()
        println("[$scenario] Server: $serverName, Client: $clientName")
        println(result.report())

        assertEquals(0, result.errorCount, "Expected zero errors but got ${result.errorCount}")
    }

    companion object {
        @Suppress("DEPRECATION")
        private val serverEngines: List<Pair<String, ApplicationEngineFactory<*, *>>> = listOf(
            "Netty" to Netty,
            "CIO" to io.ktor.server.cio.CIO,
            "Jetty" to io.ktor.server.jetty.Jetty,
            "Tomcat" to Tomcat
        )

        private val clientEngines: List<Pair<String, HttpClientEngineFactory<*>>> = listOf(
            "CIO" to io.ktor.client.engine.cio.CIO,
            "OkHttp" to OkHttp,
            "Apache5" to Apache5,
            "Java" to Java,
            "Jetty" to io.ktor.client.engine.jetty.Jetty
        )

        @JvmStatic
        fun engineCombinations(): Stream<Arguments> {
            return serverEngines.flatMap { (serverName, serverFactory) ->
                clientEngines.map { (clientName, clientFactory) ->
                    Arguments.of(serverName, clientName, serverFactory, clientFactory)
                }
            }.stream()
        }
    }
}
