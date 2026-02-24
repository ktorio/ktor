/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Result data class capturing throughput benchmark measurements.
// ABOUTME: Includes request rate, bandwidth, latency percentiles, and error count.

package io.ktor.benchmark.throughput

import kotlin.time.Duration

/**
 * Results from a throughput benchmark run.
 *
 * @property requestsPerSecond Number of successful requests per second.
 * @property megabytesPerSecond Throughput in MB/s.
 * @property latencyP50 50th percentile latency.
 * @property latencyP90 90th percentile latency.
 * @property latencyP99 99th percentile latency.
 * @property latencyP999 99.9th percentile latency.
 * @property latencyMax Maximum observed latency.
 * @property totalRequests Total number of successful requests.
 * @property errorCount Number of failed requests.
 * @property measurementDuration Actual duration of the measurement phase.
 */
public data class BenchmarkResult(
    val requestsPerSecond: Double,
    val megabytesPerSecond: Double,
    val latencyP50: Duration,
    val latencyP90: Duration,
    val latencyP99: Duration,
    val latencyP999: Duration,
    val latencyMax: Duration,
    val totalRequests: Long,
    val errorCount: Long,
    val measurementDuration: Duration
) {
    /**
     * Generates a formatted report of the benchmark results.
     */
    public fun report(): String = buildString {
        appendLine("=== Throughput Benchmark Results ===")
        appendLine("Requests/sec:    ${String.format("%,.2f", requestsPerSecond)}")
        appendLine("Throughput:      ${String.format("%,.2f", megabytesPerSecond)} MB/s")
        appendLine()
        appendLine("Latency Percentiles:")
        appendLine("  p50:           $latencyP50")
        appendLine("  p90:           $latencyP90")
        appendLine("  p99:           $latencyP99")
        appendLine("  p99.9:         $latencyP999")
        appendLine("  max:           $latencyMax")
        appendLine()
        appendLine("Total requests:  ${String.format("%,d", totalRequests)}")
        appendLine("Errors:          ${String.format("%,d", errorCount)}")
        appendLine("Duration:        $measurementDuration")
        appendLine("====================================")
    }
}
