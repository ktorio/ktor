/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Standalone profiling entry point for Netty server + Apache5 client combination.
// ABOUTME: Run with: ./gradlew :ktor-throughput-benchmark:run

package io.ktor.benchmark.throughput

import io.ktor.client.engine.apache5.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * Standalone entry point for profiling Netty server with Apache5 client.
 *
 * Usage:
 * ```
 * ./gradlew :ktor-throughput-benchmark:run
 * ```
 *
 * With custom durations:
 * ```
 * ./gradlew :ktor-throughput-benchmark:run \
 *   -Dbenchmark.duration.seconds=60 \
 *   -Dbenchmark.warmup.seconds=10
 * ```
 *
 * For profiling with async-profiler:
 * ```
 * # Start the benchmark
 * ./gradlew :ktor-throughput-benchmark:run &
 *
 * # Find the JVM PID
 * jps | grep KotlinCompileDaemon  # or look for the main class
 *
 * # Attach profiler (CPU flame graph)
 * asprof -d 30 -f profile.html <PID>
 *
 * # Or for allocation profiling
 * asprof -d 30 -e alloc -f alloc-profile.html <PID>
 * ```
 */
fun main() {
    val config = BenchmarkConfig(
        warmupDuration = System.getProperty("benchmark.warmup.seconds")
            ?.toLongOrNull()?.seconds ?: 10.seconds,
        measurementDuration = System.getProperty("benchmark.duration.seconds")
            ?.toLongOrNull()?.seconds ?: 30.seconds,
        concurrency = System.getProperty("benchmark.concurrency")
            ?.toIntOrNull() ?: 4,
        payloadSize = System.getProperty("benchmark.payload.bytes")
            ?.toIntOrNull() ?: 32 * 1024
    )

    println("=== Netty + Apache5 Profiling Session ===")
    println("Warmup:      ${config.warmupDuration}")
    println("Measurement: ${config.measurementDuration}")
    println("Concurrency: ${config.concurrency}")
    println("Payload:     ${config.payloadSize} bytes")
    println()

    val benchmark = ThroughputBenchmark(config)

    runBlocking {
        println("--- Running DOWNLOAD scenario ---")
        val downloadResult = benchmark.run(
            serverEngineFactory = Netty,
            clientEngineFactory = Apache5,
            scenario = BenchmarkScenario.DOWNLOAD
        )
        println(downloadResult.report())

        println("--- Running UPLOAD scenario ---")
        val uploadResult = benchmark.run(
            serverEngineFactory = Netty,
            clientEngineFactory = Apache5,
            scenario = BenchmarkScenario.UPLOAD
        )
        println(uploadResult.report())
    }
}
