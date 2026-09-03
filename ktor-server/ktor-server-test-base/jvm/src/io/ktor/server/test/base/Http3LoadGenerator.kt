/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.netty.handler.codec.quic.QuicException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Outcome of one HTTP/3 load run.
 *
 * Latency percentiles are the point of this type: the acceptance criteria for HTTP/3 performance
 * are comparative, and a single mean hides exactly the tail behaviour that made
 * [KTOR-9818](https://youtrack.jetbrains.com/issue/KTOR-9818) worth filing.
 */
class Http3LoadResult(
    val label: String,
    val requests: Long,
    val failures: Long,
    val bytesReceived: Long,
    val duration: Duration,
    private val latenciesNanos: LongArray,
    val inboundDatagrams: Long? = null,
    /**
     * How often a request had to wait for stream credit.
     *
     * A high count means the offered load outran the server's `MAX_STREAMS` updates, so the driver
     * — not the server — was the limit. It is reported rather than treated as a failure.
     */
    val streamCreditWaits: Long = 0,
) {
    val requestsPerSecond: Double
        get() = if (duration.inWholeNanoseconds == 0L) 0.0 else requests * 1_000_000_000.0 / duration.inWholeNanoseconds

    /** Latency at the given percentile, e.g. `0.99` for p99. Zero when nothing was measured. */
    fun latency(percentile: Double): Duration {
        if (latenciesNanos.isEmpty()) return Duration.ZERO
        val index = ((latenciesNanos.size - 1) * percentile).toInt().coerceIn(0, latenciesNanos.size - 1)
        return latenciesNanos[index].nanoseconds
    }

    fun report(): String = buildString {
        appendLine("[$label]")
        appendLine("  requests            = $requests")
        appendLine("  failures            = $failures")
        appendLine("  duration            = $duration")
        appendLine("  throughput          = ${"%.1f".format(requestsPerSecond)} req/s")
        appendLine("  bytes received      = $bytesReceived")
        appendLine("  latency p50         = ${latency(0.50)}")
        appendLine("  latency p90         = ${latency(0.90)}")
        appendLine("  latency p99         = ${latency(0.99)}")
        appendLine("  latency max         = ${latency(1.0)}")
        appendLine("  stream credit waits = $streamCreditWaits")
        if (inboundDatagrams != null) {
            appendLine("  inbound datagrams   = $inboundDatagrams")
            if (requests > 0) {
                appendLine("  datagrams / request = ${"%.2f".format(inboundDatagrams.toDouble() / requests)}")
            }
        }
    }
}

/**
 * Records measurements where a build can pick them up.
 *
 * Perf numbers are recorded rather than asserted on tightly, because CI applies
 * `testRetry { maxRetries = 1 }` and `ignoreFailures = true` to every JVM test task — a strict
 * threshold there would be retried into green rather than surfacing a regression.
 */
object Http3PerfReport {
    private val reportFile: File by lazy {
        File("build/reports/http3-perf/measurements.txt").apply {
            parentFile?.mkdirs()
            if (!exists()) writeText("HTTP/3 performance measurements\n\n")
        }
    }

    @Synchronized
    fun record(result: Http3LoadResult) {
        val report = result.report()
        print(report)
        reportFile.appendText(report + "\n")
    }

    @Synchronized
    fun record(label: String, lines: List<String>) {
        val report = buildString {
            appendLine("[$label]")
            lines.forEach { appendLine("  $it") }
        }
        print(report)
        reportFile.appendText(report + "\n")
    }
}

/**
 * Drives HTTP/3 load against [port] and returns what it measured.
 *
 * [connections] separate QUIC connections each run [streamsPerConnection] request loops, so both
 * connection-level and stream-level concurrency are exercised. Each loop issues requests back to
 * back until [duration] elapses.
 *
 * This is the QUIC analogue of [HighLoadHttpGenerator], which is TCP-only and reports counts
 * without latency.
 */
suspend fun runHttp3Load(
    label: String,
    port: Int,
    path: String = "/",
    host: String = "127.0.0.1",
    connections: Int = 4,
    streamsPerConnection: Int = 8,
    duration: Duration = 10.seconds,
    countDatagrams: Boolean = false,
): Http3LoadResult = coroutineScope {
    val datagrams = if (countDatagrams) AtomicLong() else null
    val requests = AtomicLong()
    val failures = AtomicLong()
    val creditWaits = AtomicLong()
    val bytes = AtomicLong()
    val latencies = java.util.Collections.synchronizedList(ArrayList<Long>())

    val startedAt = System.nanoTime()
    val deadline = startedAt + duration.inWholeNanoseconds

    val perConnection = (1..connections).map { connectionIndex ->
        async(Dispatchers.IO) {
            withHttp3Client(port, host, inboundDatagrams = datagrams) { connection ->
                coroutineScope {
                    (1..streamsPerConnection).map {
                        async(Dispatchers.IO) {
                            val local = ArrayList<Long>()
                            while (System.nanoTime() < deadline) {
                                val requestStart = System.nanoTime()
                                try {
                                    val response = connection.request(path = path)
                                    if (response.status == "200") {
                                        requests.incrementAndGet()
                                        bytes.addAndGet(response.body.size.toLong())
                                        local += System.nanoTime() - requestStart
                                    } else {
                                        failures.incrementAndGet()
                                    }
                                } catch (cause: QuicException) {
                                    // Out of stream credit: the offered load is ahead of the
                                    // server's MAX_STREAMS updates. Back off and retry rather than
                                    // recording a failure the server is not responsible for.
                                    creditWaits.incrementAndGet()
                                    delay(CREDIT_BACKOFF_MILLIS)
                                } catch (cause: Exception) {
                                    failures.incrementAndGet()
                                }
                            }
                            latencies.addAll(local)
                        }
                    }.awaitAll()
                }
            }
        }
    }
    perConnection.awaitAll()

    val elapsed = (System.nanoTime() - startedAt).nanoseconds
    val sorted = latencies.toLongArray().also { it.sort() }

    Http3LoadResult(
        label = label,
        requests = requests.get(),
        failures = failures.get(),
        bytesReceived = bytes.get(),
        duration = elapsed,
        latenciesNanos = sorted,
        inboundDatagrams = datagrams?.get(),
        streamCreditWaits = creditWaits.get(),
    )
}

private const val CREDIT_BACKOFF_MILLIS = 2L
