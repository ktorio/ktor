/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.netty.http3.HmacQuicTokenHandler
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.Http3PerfReport
import io.ktor.server.test.base.openHttp3Connection
import io.ktor.server.test.base.runHttp3Load
import io.ktor.server.test.base.withHttp3Client
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Performance acceptance for [KTOR-9818](https://youtrack.jetbrains.com/issue/KTOR-9818).
 *
 * Excluded from `jvmTest` by the build convention and run with `:ktor-server-netty-test-http3:stressTest`.
 *
 * These cases **record** measurements to `build/reports/http3-perf/measurements.txt` and assert only
 * coarse, stable floors. That is deliberate: CI applies `testRetry { maxRetries = 1 }` and
 * `ignoreFailures = true` to every JVM test task, so a tight threshold would be retried into green
 * instead of surfacing a regression. The recorded numbers are the artefact to compare across runs.
 *
 * The comparative HTTP/3-vs-HTTP/2 throughput criterion from the plan is **not** asserted here: the
 * ratio is measured and recorded, but the acceptance fraction still needs to be agreed with the
 * feature author. Whatever it becomes, it must sit far away from the 1/25 that motivated KTOR-9818.
 *
 * ### What these measurements found
 *
 * On the default configuration HTTP/3 reaches roughly **1/20 of HTTP/2** on the same server and
 * payload (~250 req/s against ~5,200 req/s) — the same order as the deficit KTOR-9818 was filed
 * for. The cause is stream credit, not per-request cost: a run of a few thousand requests logs
 * *over a hundred thousand* stream-credit waits even though only a few dozen streams are ever in
 * flight against a limit of 100. Completed streams are not retired promptly, so past the first
 * hundred requests every one waits on a `MAX_STREAMS` grant.
 *
 * [NettyHttp3StreamCreditStressTest] isolates it: raising `quicInitialMaxStreamsBidirectional` to
 * 1,000,000 lifts throughput to ~4,500 req/s with zero credit waits — about 17x, and ~0.87 of
 * HTTP/2. Closing the client's streams after each response was ruled out as the cause; it changes
 * nothing.
 *
 * That also makes the option's documentation misleading: it is described as a limit on *concurrent*
 * streams, but at speed it behaves as a budget for the life of the connection.
 */
@OptIn(ExperimentalKtorApi::class)
@ExtendWith(io.ktor.server.test.base.StressTestCondition::class)
class NettyHttp3StressTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp2 = true
        enableHttp3 = true
        http3Only = false
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    private val loadDuration: Duration
        get() = System.getProperty("http3.load.seconds")?.toLongOrNull()?.seconds ?: DEFAULT_LOAD

    @Test
    fun `throughput and latency percentiles are recorded`() = runTest(timeout = 5.minutes) {
        createAndStartServer {
            get("/payload") { call.respondText(SMALL_PAYLOAD) }
        }

        val result = runHttp3Load(
            label = "http3 throughput (${SMALL_PAYLOAD.length}B payload)",
            port = sslPort,
            path = "/payload",
            duration = loadDuration,
        )
        Http3PerfReport.record(result)

        assertEquals(0, result.failures, "no request may fail under load")
        assertTrue(result.requests > 0, "the load driver produced no requests at all")
        assertTrue(
            result.requestsPerSecond > MINIMUM_THROUGHPUT,
            "throughput collapsed to ${result.requestsPerSecond} req/s, below the sanity floor"
        )
    }

    /**
     * Measures HTTP/3 and HTTP/2 against the same server and payload and records the ratio.
     *
     * The two legs use different client stacks — a raw Netty QUIC client against Apache's HTTP/2 —
     * so the ratio is a trend indicator, not a clean protocol comparison. It is recorded for
     * exactly that: to notice a collapse of the kind KTOR-9818 describes.
     */
    @Test
    fun `throughput is recorded alongside HTTP2 for comparison`() = runTest(timeout = 5.minutes) {
        createAndStartServer {
            get("/payload") { call.respondText(SMALL_PAYLOAD) }
        }

        val http3 = runHttp3Load(
            label = "http3 leg of the HTTP/2 comparison",
            port = sslPort,
            path = "/payload",
            duration = loadDuration,
        )
        Http3PerfReport.record(http3)

        val http2Requests = measureHttp2Throughput(loadDuration)
        val http2PerSecond = http2Requests / loadDuration.inWholeMilliseconds.toDouble() * 1000

        Http3PerfReport.record(
            "http3 vs http2 throughput",
            listOf(
                "http3 = ${"%.1f".format(http3.requestsPerSecond)} req/s",
                "http2 = ${"%.1f".format(http2PerSecond)} req/s",
                "ratio = ${"%.2f".format(http3.requestsPerSecond / http2PerSecond)}",
                "NOTE: acceptance fraction still to be agreed with the feature author",
            )
        )

        assertEquals(0, http3.failures, "no HTTP/3 request may fail under load")
        assertTrue(http2Requests > 0, "the HTTP/2 comparison leg produced no requests")
    }

    /**
     * Records how many datagrams the client receives per small request, as a regression signal for
     * the `shutdownOutput()` coalescing PR #5822 introduced.
     *
     * This deliberately asserts nothing about coalescing. The counter sits on the client's datagram
     * channel, below QUIC decryption, so it counts every inbound datagram — acknowledgements and
     * connection-maintenance packets included — and cannot tell whether a response's last DATA
     * frame and its stream FIN shared a datagram. Proving that needs packet-level inspection of the
     * decrypted stream, which this harness cannot do. The measured figure is still useful compared
     * against itself over time: a jump means more datagrams per response than before.
     */
    @Test
    fun `datagrams per small response are recorded`() = runTest {
        createAndStartServer {
            get("/tiny") { call.respondText("ok") }
        }

        val datagrams = AtomicLong()
        withHttp3Client(sslPort, inboundDatagrams = datagrams) { connection ->
            // Warm up so handshake datagrams are not attributed to the measured requests.
            connection.request(path = "/tiny")
            val afterHandshake = datagrams.get()

            repeat(REQUESTS_PER_DATAGRAM_SAMPLE) { connection.request(path = "/tiny") }
            val perRequest =
                (datagrams.get() - afterHandshake).toDouble() / REQUESTS_PER_DATAGRAM_SAMPLE

            Http3PerfReport.record(
                "response packetisation",
                listOf(
                    "handshake datagrams   = $afterHandshake",
                    "datagrams per request = ${"%.2f".format(perRequest)} (includes ACK-only datagrams)",
                )
            )

            assertTrue(perRequest > 0, "no inbound datagrams were counted at all")
        }
    }

    /** Records the handshake cost that enabling stateless Retry adds. */
    @Test
    fun `the handshake cost of address validation is recorded`() = runTest(timeout = 5.minutes) {
        createAndStartServer {
            get("/ping") { call.respondText("pong") }
        }

        val withoutRetry = measureHandshakes(sslPort, HANDSHAKE_SAMPLES)
        Http3PerfReport.record(
            "handshake cost",
            listOf(
                "without retry (default) = ${withoutRetry / HANDSHAKE_SAMPLES} ns mean over " +
                    "$HANDSHAKE_SAMPLES handshakes",
                "NOTE: the retry-enabled comparison runs in NettyHttp3RetryStressTest",
            )
        )

        assertTrue(withoutRetry > 0, "no handshake was measured")
    }

    /**
     * The soak run. Its duration comes from `-Dhttp3.soak.seconds`, defaulting to something short
     * enough for an ordinary stress run; the 30+ minute soak the plan calls for is the same test
     * with a longer value, ideally alongside `-Dio.netty.leakDetection.level=paranoid`.
     */
    @Test
    fun `a sustained run leaks neither memory nor sockets`() = runTest(timeout = 60.minutes) {
        createAndStartServer {
            get("/payload") { call.respondText(SMALL_PAYLOAD) }
        }

        val soak = System.getProperty("http3.soak.seconds")?.toLongOrNull()?.seconds ?: DEFAULT_SOAK

        val runtime = Runtime.getRuntime()
        System.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()

        val result = runHttp3Load(
            label = "http3 soak ($soak)",
            port = sslPort,
            path = "/payload",
            duration = soak,
        )
        Http3PerfReport.record(result)

        System.gc()
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        Http3PerfReport.record(
            "soak heap",
            listOf(
                "heap before = $heapBefore bytes",
                "heap after  = $heapAfter bytes",
                "growth      = ${heapAfter - heapBefore} bytes",
            )
        )

        assertEquals(0, result.failures, "no request may fail during the soak")
        assertTrue(
            heapAfter < heapBefore + MAX_HEAP_GROWTH,
            "retained heap grew by ${heapAfter - heapBefore} bytes over the soak"
        )
    }

    private suspend fun measureHandshakes(port: Int, samples: Int): Long = withContext(Dispatchers.IO) {
        var total = 0L
        repeat(samples) {
            val start = System.nanoTime()
            val connection = openHttp3Connection(port)
            total += System.nanoTime() - start
            connection.close()
        }
        total
    }

    private suspend fun measureHttp2Throughput(duration: Duration): Long {
        val deadline = System.nanoTime() + duration.inWholeNanoseconds
        var count = 0L
        val client = http2Client ?: createApacheClient().also { http2Client = it }

        while (System.nanoTime() < deadline) {
            val body = client.get("https://127.0.0.1:$sslPort/payload").bodyAsText()
            if (body == SMALL_PAYLOAD) count++
        }
        return count
    }

    private companion object {
        private val DEFAULT_LOAD = 10.seconds
        private val DEFAULT_SOAK = 30.seconds

        private val SMALL_PAYLOAD = "x".repeat(1024)

        /** A floor, not a target: below this the transport is broken rather than slow. */
        private const val MINIMUM_THROUGHPUT = 20.0

        private const val HANDSHAKE_SAMPLES = 20
        private const val REQUESTS_PER_DATAGRAM_SAMPLE = 50

        private const val MAX_HEAP_GROWTH = 256L * 1024 * 1024
    }
}

/**
 * Address-validation half of the handshake-cost measure, which needs a differently
 * configured engine.
 */
@OptIn(ExperimentalKtorApi::class)
@ExtendWith(io.ktor.server.test.base.StressTestCondition::class)
class NettyHttp3RetryStressTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3 { quicTokenHandler = HmacQuicTokenHandler() }
    }

    @Test
    fun `the handshake cost with address validation is recorded`() = runTest(timeout = 5.minutes) {
        createAndStartServer {
            get("/ping") { call.respondText("pong") }
        }

        var total = 0L
        withContext(Dispatchers.IO) {
            repeat(SAMPLES) {
                val start = System.nanoTime()
                val connection = openHttp3Connection(sslPort)
                total += System.nanoTime() - start
                connection.close()
            }
        }

        Http3PerfReport.record(
            "handshake cost with retry",
            listOf(
                "with retry = ${total / SAMPLES} ns mean over $SAMPLES handshakes",
                "compare against the default-path figure recorded by NettyHttp3StressTest",
            )
        )

        assertTrue(total > 0, "no handshake was measured")
    }

    private companion object {
        private const val SAMPLES = 20
    }
}

/**
 * Multi-socket scaling. Kernel-side UDP load balancing across `SO_REUSEPORT` sockets is
 * Linux-only, so this records a comparison only there; elsewhere all datagrams land on one socket
 * and the numbers would say nothing about scaling.
 */
@OptIn(ExperimentalKtorApi::class)
@ExtendWith(io.ktor.server.test.base.StressTestCondition::class)
class NettyHttp3MultiSocketStressTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3 { udpSocketCount = SOCKETS }
    }

    @Test
    fun `throughput with several udp sockets is recorded`() = runTest(timeout = 5.minutes) {
        assumeTrue(
            System.getProperty("os.name").lowercase().contains("linux"),
            "kernel UDP balancing across reuseport sockets is Linux-only"
        )

        createAndStartServer {
            get("/payload") { call.respondText("x".repeat(1024)) }
        }

        val result = runHttp3Load(
            label = "http3 throughput with udpSocketCount=$SOCKETS",
            port = sslPort,
            path = "/payload",
            connections = 16,
            duration = 10.seconds,
        )
        Http3PerfReport.record(result)

        assertEquals(0, result.failures, "no request may fail under load")
    }

    private companion object {
        private const val SOCKETS = 4
    }
}

/**
 * Isolates whether bidirectional stream credit is what limits throughput.
 *
 * The default run shows tens of thousands of stream-credit waits for a few thousand requests, even
 * though no more than a few dozen streams are ever in flight against a limit of 100. That can only
 * happen if completed streams are not retired promptly, making every request past the first hundred
 * wait for a `MAX_STREAMS` grant.
 *
 * This class raises `quicInitialMaxStreamsBidirectional` far beyond anything the run can consume.
 * If throughput jumps, credit replenishment is the bottleneck and the default of 100 is a practical
 * cap on requests per connection; if it does not, the cost lies elsewhere and this rules stream
 * credit out. Either way the numbers are recorded rather than asserted.
 */
@OptIn(ExperimentalKtorApi::class)
@ExtendWith(io.ktor.server.test.base.StressTestCondition::class)
class NettyHttp3StreamCreditStressTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3 { quicInitialMaxStreamsBidirectional = 1_000_000 }
    }

    @Test
    fun `throughput with a raised stream limit is recorded`() = runTest(timeout = 5.minutes) {
        createAndStartServer {
            get("/payload") { call.respondText("x".repeat(1024)) }
        }

        val result = runHttp3Load(
            label = "http3 throughput with quicInitialMaxStreamsBidirectional=1000000",
            port = sslPort,
            path = "/payload",
            duration = 10.seconds,
        )
        Http3PerfReport.record(result)

        assertEquals(0, result.failures, "no request may fail under load")
        assertTrue(result.requests > 0, "the load driver produced no requests at all")
    }
}
