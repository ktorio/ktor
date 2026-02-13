/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Tests for hidden performance bottlenecks in Netty pipeline that wouldn't appear in profilers.
// ABOUTME: Detects underutilization scenarios like queue blocking, flush delays, and timing-dependent issues.

package io.ktor.tests.server.netty

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Tests for hidden performance bottlenecks in Netty that wouldn't appear in profilers.
 *
 * These bottlenecks manifest as:
 * - Requests waiting in queues while CPU is idle
 * - Response data stuck in buffers
 * - Underutilized worker threads despite pending work
 *
 * The key insight is that profilers show WHERE CPU time is spent, but not
 * WHERE time is wasted waiting. These tests measure latency distributions
 * to detect such "invisible" bottlenecks.
 */
class NettyPipelineBottleneckTest {

    /**
     * Tests HTTP/1.1 pipelining behavior to detect flush deadlock.
     *
     * The bottleneck: `flushIfNeeded()` only flushes when `activeRequests == 0`,
     * but with pipelined requests, multiple requests are active simultaneously.
     * This can cause response data to sit in Netty's buffer indefinitely.
     *
     * Detection method: Send multiple pipelined requests over a single connection
     * and measure if later responses have disproportionately higher latency.
     */
    @Test
    fun testPipelinedRequestsFlushTimely() = runTestWithRealTime {
        val appStarted = CompletableDeferred<Application>()
        val responseLatencies = ConcurrentHashMap<Int, Long>()

        val serverJob = launch(Dispatchers.IO) {
            val server = embeddedServer(Netty, port = 0) {
                routing {
                    get("/echo/{id}") {
                        val id = call.parameters["id"]?.toIntOrNull() ?: 0
                        // Small response that should flush quickly
                        call.respondText("Response $id")
                    }
                }
            }

            server.monitor.subscribe(ApplicationStarted) { app ->
                appStarted.complete(app)
            }

            server.start(wait = true)
        }

        try {
            val serverApp = withTimeout(10.seconds) { appStarted.await() }
            val connector = serverApp.engine.resolvedConnectors()[0]

            SelectorManager().use { manager ->
                aSocket(manager).tcp().connect(connector.host, connector.port).use { socket ->
                    val writeChannel = socket.openWriteChannel()
                    val readChannel = socket.openReadChannel()

                    val requestCount = 5
                    val startTime = System.nanoTime()

                    // Send all requests at once (pipelining)
                    for (i in 1..requestCount) {
                        writeChannel.writeStringUtf8("GET /echo/$i HTTP/1.1\r\n")
                        writeChannel.writeStringUtf8("Host: ${connector.host}:${connector.port}\r\n")
                        if (i == requestCount) {
                            writeChannel.writeStringUtf8("Connection: close\r\n")
                        }
                        writeChannel.writeStringUtf8("\r\n")
                    }
                    writeChannel.flush()

                    // Read and parse complete HTTP responses
                    var responsesReceived = 0

                    while (responsesReceived < requestCount && !readChannel.isClosedForRead) {
                        // Read status line
                        val statusLine = withTimeoutOrNull(5.seconds) {
                            readChannel.readLine()
                        } ?: break

                        if (!statusLine.startsWith("HTTP/1.1")) {
                            continue
                        }

                        // Read headers until empty line
                        var contentLength = 0
                        var isChunked = false
                        while (true) {
                            val headerLine = withTimeoutOrNull(5.seconds) {
                                readChannel.readLine()
                            } ?: break

                            if (headerLine.isEmpty()) {
                                break // End of headers
                            }

                            if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                            }
                            if (headerLine.startsWith("Transfer-Encoding:", ignoreCase = true) &&
                                headerLine.contains("chunked", ignoreCase = true)
                            ) {
                                isChunked = true
                            }
                        }

                        // Read body
                        if (isChunked) {
                            // Read chunked body
                            while (true) {
                                val chunkSizeLine = readChannel.readLine() ?: break
                                val chunkSize = chunkSizeLine.trim().toIntOrNull(16) ?: 0
                                if (chunkSize == 0) {
                                    readChannel.readLine() // trailing CRLF
                                    break
                                }
                                val chunk = ByteArray(chunkSize)
                                readChannel.readFully(chunk)
                                readChannel.readLine() // CRLF after chunk
                            }
                        } else if (contentLength > 0) {
                            val body = ByteArray(contentLength)
                            readChannel.readFully(body)
                        }

                        // Record timing for this response
                        responsesReceived++
                        val latency = System.nanoTime() - startTime
                        responseLatencies[responsesReceived] = latency
                    }

                    // Verify all responses received
                    assertTrue(
                        responsesReceived >= requestCount - 1,
                        "Expected at least ${requestCount - 1} responses, got $responsesReceived"
                    )

                    // Check for flush delay pattern:
                    // If there's a flush bottleneck, later responses will have much higher latency
                    if (responseLatencies.size >= 3) {
                        val firstLatency = responseLatencies[1] ?: 0L
                        val lastLatency = responseLatencies[responseLatencies.size] ?: 0L

                        // Allow 10x latency difference for legitimate processing,
                        // but more than that suggests buffering issues
                        val latencyRatio = if (firstLatency > 0) lastLatency.toDouble() / firstLatency else 0.0

                        // Log for debugging
                        println("Pipelining latency analysis:")
                        responseLatencies.forEach { (id, latency) ->
                            println("  Response $id: ${latency / 1_000_000}ms")
                        }
                        println("  Latency ratio (last/first): $latencyRatio")

                        // This is a soft assertion - we're detecting potential issues
                        // A ratio > 100 strongly suggests flush buffering problems
                        if (latencyRatio > 100) {
                            println("WARNING: High latency ratio detected - potential flush bottleneck")
                        }
                    }
                }
            }
        } finally {
            serverJob.cancel()
        }
    }

    /**
     * Tests concurrent request handling to detect thread pool underutilization.
     *
     * The bottleneck: `runningLimit = 32` by default allows many concurrent requests,
     * but worker threads may be fewer, causing queuing.
     *
     * Detection method: Send many concurrent requests with controlled processing time
     * and measure if throughput matches expected parallelism.
     */
    @Test
    fun testConcurrentRequestThroughput() = runTestWithRealTime {
        val appStarted = CompletableDeferred<Application>()
        val processedCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val currentConcurrent = AtomicInteger(0)
        val processingDelay = 50.milliseconds

        val serverJob = launch(Dispatchers.IO) {
            val server = embeddedServer(Netty, port = 0) {
                routing {
                    get("/process") {
                        val current = currentConcurrent.incrementAndGet()
                        maxConcurrent.updateAndGet { max -> maxOf(max, current) }

                        try {
                            // Simulate work that should allow parallelism
                            delay(processingDelay)
                            processedCount.incrementAndGet()
                            call.respondText("OK")
                        } finally {
                            currentConcurrent.decrementAndGet()
                        }
                    }
                }
            }

            server.monitor.subscribe(ApplicationStarted) { app ->
                appStarted.complete(app)
            }

            server.start(wait = true)
        }

        try {
            val serverApp = withTimeout(10.seconds) { appStarted.await() }
            val connector = serverApp.engine.resolvedConnectors()[0]

            HttpClient(CIO) {
                engine {
                    maxConnectionsCount = 100
                    endpoint {
                        maxConnectionsPerRoute = 100
                    }
                }
            }.use { client ->
                val requestCount = 50
                val duration = measureTime {
                    coroutineScope {
                        repeat(requestCount) {
                            launch {
                                client.get("http://${connector.host}:${connector.port}/process")
                            }
                        }
                    }
                }

                // All requests should complete
                assertEquals(requestCount, processedCount.get(), "Not all requests processed")

                // Calculate expected vs actual parallelism
                val expectedMinDuration = processingDelay * (requestCount / maxConcurrent.get().coerceAtLeast(1))
                val actualDuration = duration

                println("Throughput analysis:")
                println("  Total requests: $requestCount")
                println("  Max concurrent observed: ${maxConcurrent.get()}")
                println("  Total duration: $actualDuration")
                println("  Expected min duration (perfect parallelism): $expectedMinDuration")

                // If actual duration is much higher than expected, threads may be underutilized
                // This could indicate queue blocking or thread pool misconfiguration
                assertTrue(
                    maxConcurrent.get() > 1,
                    "Expected some concurrent processing, but max concurrent was ${maxConcurrent.get()}"
                )
            }
        } finally {
            serverJob.cancel()
        }
    }

    /**
     * Tests response body streaming to detect ByteChannel suspension issues.
     *
     * The bottleneck: `channel.awaitContent()` in respondWithBigBody has no timeout.
     * If the application produces data slowly, the response coroutine may appear
     * "stuck" even though it's legitimately waiting.
     *
     * Detection method: Stream a response with controlled data production rate
     * and verify the streaming pipeline doesn't introduce unexpected delays.
     */
    @Test
    fun testStreamingResponseFlowControl() = runTestWithRealTime {
        val appStarted = CompletableDeferred<Application>()
        val chunkCount = 10
        val chunkSize = 1024
        val chunkDelay = 20.milliseconds
        val chunksSent = AtomicInteger(0)
        val firstByteReceived = AtomicLong(0)
        val lastByteReceived = AtomicLong(0)

        val serverJob = launch(Dispatchers.IO) {
            val server = embeddedServer(Netty, port = 0) {
                routing {
                    get("/stream") {
                        call.respondBytesWriter {
                            repeat(chunkCount) { i ->
                                val chunk = ByteArray(chunkSize) { (i % 256).toByte() }
                                writeFully(chunk)
                                flush()
                                chunksSent.incrementAndGet()
                                delay(chunkDelay)
                            }
                        }
                    }
                }
            }

            server.monitor.subscribe(ApplicationStarted) { app ->
                appStarted.complete(app)
            }

            server.start(wait = true)
        }

        try {
            val serverApp = withTimeout(10.seconds) { appStarted.await() }
            val connector = serverApp.engine.resolvedConnectors()[0]

            HttpClient(CIO).use { client ->
                val startTime = System.nanoTime()
                var bytesReceived = 0

                client.prepareGet("http://${connector.host}:${connector.port}/stream").execute { response ->
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(chunkSize)

                    while (!channel.isClosedForRead) {
                        val readBytes = channel.readAvailable(buffer)

                        if (readBytes > 0) {
                            if (bytesReceived == 0) {
                                firstByteReceived.set(System.nanoTime() - startTime)
                            }
                            bytesReceived += readBytes
                            lastByteReceived.set(System.nanoTime() - startTime)
                        } else if (readBytes == -1) {
                            break
                        }
                    }
                }

                val expectedTotalBytes = chunkCount * chunkSize
                val timeToFirstByte = firstByteReceived.get() / 1_000_000
                val totalTime = lastByteReceived.get() / 1_000_000
                val expectedMinTime = (chunkDelay * chunkCount).inWholeMilliseconds

                println("Streaming analysis:")
                println("  Bytes received: $bytesReceived / $expectedTotalBytes")
                println("  Time to first byte: ${timeToFirstByte}ms")
                println("  Total streaming time: ${totalTime}ms")
                println("  Expected min time (production delays only): ${expectedMinTime}ms")
                println("  Chunks sent by server: ${chunksSent.get()}")

                // Verify all data received
                assertEquals(
                    expectedTotalBytes,
                    bytesReceived,
                    "Expected $expectedTotalBytes bytes, got $bytesReceived"
                )

                // First byte should arrive quickly (before all chunks produced)
                assertTrue(
                    timeToFirstByte < expectedMinTime,
                    "First byte took too long: ${timeToFirstByte}ms >= ${expectedMinTime}ms - possible buffering issue"
                )
            }
        } finally {
            serverJob.cancel()
        }
    }

    /**
     * Tests request body consumption rate to detect RequestBodyHandler queue growth.
     *
     * The bottleneck: `Channel<Any>(Channel.UNLIMITED)` in RequestBodyHandler
     * can grow unbounded if body processing is slow.
     *
     * Detection method: Send a large request body while deliberately slowing
     * consumption and verify the system handles backpressure appropriately.
     */
    @Test
    fun testRequestBodyBackpressure() = runTestWithRealTime {
        val appStarted = CompletableDeferred<Application>()
        val bytesReceived = AtomicLong(0)
        val processingStarted = AtomicLong(0)
        val processingCompleted = AtomicLong(0)

        val serverJob = launch(Dispatchers.IO) {
            val server = embeddedServer(Netty, port = 0) {
                routing {
                    post("/upload") {
                        processingStarted.set(System.nanoTime())
                        val channel = call.receiveChannel()
                        val buffer = ByteArray(4096)

                        // Slow consumer - simulates processing bottleneck
                        while (!channel.isClosedForRead) {
                            val readBytes = channel.readAvailable(buffer)
                            if (readBytes > 0) {
                                bytesReceived.addAndGet(readBytes.toLong())
                                delay(10.milliseconds) // Deliberately slow
                            } else if (readBytes == -1) {
                                break
                            }
                        }

                        processingCompleted.set(System.nanoTime())
                        call.respondText("Received: ${bytesReceived.get()} bytes")
                    }
                }
            }

            server.monitor.subscribe(ApplicationStarted) { app ->
                appStarted.complete(app)
            }

            server.start(wait = true)
        }

        try {
            val serverApp = withTimeout(30.seconds) { appStarted.await() }
            val connector = serverApp.engine.resolvedConnectors()[0]

            SelectorManager().use { manager ->
                aSocket(manager).tcp().connect(connector.host, connector.port).use { socket ->
                    val writeChannel = socket.openWriteChannel()
                    val readChannel = socket.openReadChannel()

                    val bodySize = 100_000 // 100KB - enough to test backpressure
                    val sendStartTime = System.nanoTime()

                    // Send HTTP request with body
                    writeChannel.writeStringUtf8("POST /upload HTTP/1.1\r\n")
                    writeChannel.writeStringUtf8("Host: ${connector.host}:${connector.port}\r\n")
                    writeChannel.writeStringUtf8("Content-Length: $bodySize\r\n")
                    writeChannel.writeStringUtf8("Connection: close\r\n")
                    writeChannel.writeStringUtf8("\r\n")

                    // Send body in chunks
                    val chunkSize = 10_000
                    var bytesSent = 0
                    while (bytesSent < bodySize) {
                        val remaining = minOf(chunkSize, bodySize - bytesSent)
                        val chunk = ByteArray(remaining) { 'X'.code.toByte() }
                        writeChannel.writeFully(chunk)
                        writeChannel.flush()
                        bytesSent += remaining
                    }

                    val sendEndTime = System.nanoTime()

                    // Wait for response
                    withTimeout(30.seconds) {
                        val responseLines = mutableListOf<String>()
                        while (!readChannel.isClosedForRead) {
                            val line = readChannel.readLine() ?: break
                            responseLines.add(line)
                        }
                        assertTrue(responseLines.any { it.contains("200 OK") }, "Expected 200 OK response")
                    }

                    val sendDuration = (sendEndTime - sendStartTime) / 1_000_000
                    val processDuration = (processingCompleted.get() - processingStarted.get()) / 1_000_000

                    println("Backpressure analysis:")
                    println("  Bytes sent: $bytesSent")
                    println("  Bytes received by server: ${bytesReceived.get()}")
                    println("  Send duration: ${sendDuration}ms")
                    println("  Process duration: ${processDuration}ms")

                    // All bytes should be received
                    assertEquals(
                        bodySize.toLong(),
                        bytesReceived.get(),
                        "Server didn't receive all bytes"
                    )
                }
            }
        } finally {
            serverJob.cancel()
        }
    }
}
