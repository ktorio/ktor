/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Standalone entry point for profiling big file transfer throughput.
// ABOUTME: Run with: ./gradlew :ktor-throughput-benchmark:runBigFile

package io.ktor.benchmark.throughput

import io.ktor.client.engine.apache5.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * Standalone entry point for profiling big file transfer.
 *
 * Usage:
 * ```
 * ./gradlew :ktor-throughput-benchmark:runBigFile
 * ```
 *
 * With custom settings:
 * ```
 * ./gradlew :ktor-throughput-benchmark:runBigFile \
 *   -Dbenchmark.filesize.mb=100 \
 *   -Dbenchmark.duration.seconds=60 \
 *   -Dbenchmark.concurrency=8
 * ```
 */
fun main() {
    val config = BigFileConfig(
        fileSizeMB = System.getProperty("benchmark.filesize.mb")
            ?.toIntOrNull() ?: 100,
        warmupDuration = System.getProperty("benchmark.warmup.seconds")
            ?.toLongOrNull()?.seconds ?: 5.seconds,
        measurementDuration = System.getProperty("benchmark.duration.seconds")
            ?.toLongOrNull()?.seconds ?: 30.seconds,
        concurrency = System.getProperty("benchmark.concurrency")
            ?.toIntOrNull() ?: 4,
        useFileContent = System.getProperty("benchmark.use.file")
            ?.toBoolean() ?: true
    )

    println("=== Big File Transfer Benchmark ===")
    println("File size:    ${config.fileSizeMB} MB")
    println("Warmup:       ${config.warmupDuration}")
    println("Measurement:  ${config.measurementDuration}")
    println("Concurrency:  ${config.concurrency}")
    println("Source:       ${if (config.useFileContent) "LocalFileContent" else "In-memory ByteArray"}")
    println()

    val benchmark = BigFileTransferBenchmark(config)

    runBlocking {
        val result = benchmark.run(
            serverEngineFactory = Netty,
            clientEngineFactory = Apache5
        )
        println(result.report())

        // Compare with theoretical max
        println("=== Analysis ===")
        println("Localhost loopback typically supports 10-40 Gbps")
        println("Your throughput: %.2f Gbps".format(result.gigabitsPerSecond))
    }
}
