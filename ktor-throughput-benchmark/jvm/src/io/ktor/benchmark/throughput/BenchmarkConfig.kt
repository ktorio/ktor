/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Configuration data class for throughput benchmarks.
// ABOUTME: All settings are overridable via system properties.

package io.ktor.benchmark.throughput

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for throughput benchmarks.
 *
 * @property warmupDuration Duration for warming up the JVM before measurement.
 * @property measurementDuration Duration of the actual measurement phase.
 * @property concurrency Number of concurrent coroutines making requests.
 * @property payloadSize Size of the payload in bytes for upload/download tests.
 */
public data class BenchmarkConfig(
    val warmupDuration: Duration = System.getProperty("benchmark.warmup.seconds")
        ?.toLongOrNull()?.seconds ?: 10.seconds,
    val measurementDuration: Duration = System.getProperty("benchmark.duration.seconds")
        ?.toLongOrNull()?.seconds ?: 60.seconds,
    val concurrency: Int = System.getProperty("benchmark.concurrency")
        ?.toIntOrNull() ?: (Runtime.getRuntime().availableProcessors() * 4),
    val payloadSize: Int = System.getProperty("benchmark.payload.bytes")
        ?.toIntOrNull() ?: 32 * 1024
) {
    init {
        require(warmupDuration.isPositive()) { "warmupDuration must be positive" }
        require(measurementDuration.isPositive()) { "measurementDuration must be positive" }
        require(concurrency > 0) { "concurrency must be positive" }
        require(payloadSize > 0) { "payloadSize must be positive" }
    }
}
