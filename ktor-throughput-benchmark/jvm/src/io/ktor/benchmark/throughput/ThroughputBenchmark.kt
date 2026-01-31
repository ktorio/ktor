/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Core benchmark engine for measuring HTTP throughput between Ktor client and server.
// ABOUTME: Supports warmup phase, concurrent request generation, and latency percentile calculation.

package io.ktor.benchmark.throughput

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

/**
 * Benchmark scenario type.
 */
public enum class BenchmarkScenario {
    DOWNLOAD,
    UPLOAD
}

/**
 * Core throughput benchmark engine.
 *
 * This class orchestrates the benchmark by:
 * 1. Starting an embedded server with upload/download routes
 * 2. Creating an HttpClient with the specified engine
 * 3. Running a warmup phase
 * 4. Running the measurement phase with concurrent coroutines
 * 5. Computing latency percentiles from collected samples
 */
public class ThroughputBenchmark(
    private val config: BenchmarkConfig = BenchmarkConfig()
) {
    private val payload: ByteArray = ByteArray(config.payloadSize) { it.toByte() }

    /**
     * Runs the benchmark with the specified server and client engine factories.
     *
     * @param serverEngineFactory Factory for creating the server engine.
     * @param clientEngineFactory Factory for creating the client engine.
     * @param scenario The benchmark scenario (DOWNLOAD or UPLOAD).
     * @return The benchmark results.
     */
    public suspend fun <
        TServerEngine : ApplicationEngine,
        TServerConfig : ApplicationEngine.Configuration,
        TClientConfig : HttpClientEngineConfig
        > run(
        serverEngineFactory: ApplicationEngineFactory<TServerEngine, TServerConfig>,
        clientEngineFactory: HttpClientEngineFactory<TClientConfig>,
        scenario: BenchmarkScenario
    ): BenchmarkResult = coroutineScope {
        val port = findFreePort()

        val server = embeddedServer(serverEngineFactory, port = port) {
            routing {
                get("/download") {
                    call.respondBytes(payload, ContentType.Application.OctetStream)
                }
                post("/upload") {
                    call.receiveChannel().discard()
                    call.respondText("OK")
                }
            }
        }

        server.start(wait = false)

        try {
            HttpClient(clientEngineFactory).use { client ->
                val baseUrl = "http://127.0.0.1:$port"

                // Warmup phase
                runPhase(client, baseUrl, scenario, config.warmupDuration, collectLatencies = false)

                // Measurement phase
                runPhase(client, baseUrl, scenario, config.measurementDuration, collectLatencies = true)
            }
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
        }
    }

    private suspend fun runPhase(
        client: HttpClient,
        baseUrl: String,
        scenario: BenchmarkScenario,
        duration: Duration,
        collectLatencies: Boolean
    ): BenchmarkResult = coroutineScope {
        val latencies = ConcurrentLinkedQueue<Long>()
        val requestCount = AtomicLong(0)
        val errorCount = AtomicLong(0)
        val bytesTransferred = AtomicLong(0)

        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()

        val jobs = (1..config.concurrency).map {
            launch(Dispatchers.IO) {
                while (startMark.elapsedNow() < duration) {
                    val requestStart = System.nanoTime()
                    try {
                        when (scenario) {
                            BenchmarkScenario.DOWNLOAD -> {
                                // Use streaming API to avoid SaveBody double-copy
                                client.prepareGet("$baseUrl/download").execute { response ->
                                    val channel = response.bodyAsChannel()
                                    val bytes = channel.toByteArray()
                                    bytesTransferred.addAndGet(bytes.size.toLong())
                                }
                            }
                            BenchmarkScenario.UPLOAD -> {
                                client.post("$baseUrl/upload") {
                                    setBody(payload)
                                }
                                bytesTransferred.addAndGet(payload.size.toLong())
                            }
                        }
                        requestCount.incrementAndGet()
                        if (collectLatencies) {
                            latencies.add(System.nanoTime() - requestStart)
                        }
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                    }
                }
            }
        }

        jobs.forEach { it.join() }

        val actualDuration = startMark.elapsedNow()
        computeResult(
            latencies = latencies.toList(),
            requestCount = requestCount.get(),
            errorCount = errorCount.get(),
            bytesTransferred = bytesTransferred.get(),
            duration = actualDuration
        )
    }

    private fun computeResult(
        latencies: List<Long>,
        requestCount: Long,
        errorCount: Long,
        bytesTransferred: Long,
        duration: Duration
    ): BenchmarkResult {
        val durationSeconds = duration.inWholeMilliseconds / 1000.0
        val requestsPerSecond = if (durationSeconds > 0) requestCount / durationSeconds else 0.0
        val megabytesPerSecond = if (durationSeconds > 0) {
            (bytesTransferred / (1024.0 * 1024.0)) / durationSeconds
        } else {
            0.0
        }

        val sortedLatencies = latencies.sorted()
        val p50 = percentile(sortedLatencies, 50.0)
        val p90 = percentile(sortedLatencies, 90.0)
        val p99 = percentile(sortedLatencies, 99.0)
        val p999 = percentile(sortedLatencies, 99.9)
        val max = sortedLatencies.lastOrNull() ?: 0L

        return BenchmarkResult(
            requestsPerSecond = requestsPerSecond,
            megabytesPerSecond = megabytesPerSecond,
            latencyP50 = p50.nanoseconds,
            latencyP90 = p90.nanoseconds,
            latencyP99 = p99.nanoseconds,
            latencyP999 = p999.nanoseconds,
            latencyMax = max.nanoseconds,
            totalRequests = requestCount,
            errorCount = errorCount,
            measurementDuration = duration
        )
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Long {
        if (sortedValues.isEmpty()) return 0L
        val index = ((percentile / 100.0) * sortedValues.size).toInt()
            .coerceIn(0, sortedValues.size - 1)
        return sortedValues[index]
    }

    private fun findFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }
}
