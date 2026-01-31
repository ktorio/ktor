/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Benchmark for measuring maximum data transfer throughput with large files.
// ABOUTME: Tests streaming performance to find network/IO bottlenecks.

package io.ktor.benchmark.throughput

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Configuration for big file transfer benchmark.
 */
public data class BigFileConfig(
    val fileSizeMB: Int = 100,
    val warmupDuration: Duration = 5.seconds,
    val measurementDuration: Duration = 30.seconds,
    val concurrency: Int = 4,
    /** true = LocalFileContent, false = in-memory ByteArray */
    val useFileContent: Boolean = true
)

/**
 * Result of big file transfer benchmark.
 */
public data class BigFileResult(
    val megabytesPerSecond: Double,
    val gigabitsPerSecond: Double,
    val totalBytesTransferred: Long,
    val transferCount: Long,
    val measurementDuration: Duration
) {
    public fun report(): String = buildString {
        appendLine("=== Big File Transfer Results ===")
        appendLine("Throughput:      %.2f MB/s (%.2f Gbps)".format(megabytesPerSecond, gigabitsPerSecond))
        appendLine("Total transferred: %.2f GB".format(totalBytesTransferred / (1024.0 * 1024.0 * 1024.0)))
        appendLine("Transfers:       $transferCount")
        appendLine("Duration:        $measurementDuration")
    }
}

/**
 * Benchmark for measuring maximum data transfer throughput.
 *
 * This benchmark focuses on raw transfer speed rather than request/response overhead.
 * It creates a large file and measures how fast it can be transferred over HTTP.
 */
public class BigFileTransferBenchmark(
    private val config: BigFileConfig = BigFileConfig()
) {
    private val testFile: File by lazy { createTestFile() }
    private val inMemoryData: ByteArray by lazy { ByteArray(config.fileSizeMB * 1024 * 1024) { it.toByte() } }

    private fun createTestFile(): File {
        val file = File("build/benchmark-test-file-${config.fileSizeMB}mb.dat")
        if (!file.exists() || file.length() != config.fileSizeMB * 1024L * 1024L) {
            println("Creating test file: ${file.absolutePath} (${config.fileSizeMB} MB)")
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(config.fileSizeMB * 1024L * 1024L)
                // Write some pattern to ensure it's not sparse
                val buffer = ByteArray(1024 * 1024) { it.toByte() }
                repeat(config.fileSizeMB) { i ->
                    raf.seek(i * 1024L * 1024L)
                    raf.write(buffer)
                }
            }
            println("Test file created: ${file.length()} bytes")
        }
        return file
    }

    /**
     * Runs the big file transfer benchmark.
     */
    public suspend fun <
        TServerEngine : ApplicationEngine,
        TServerConfig : ApplicationEngine.Configuration,
        TClientConfig : HttpClientEngineConfig
        > run(
        serverEngineFactory: ApplicationEngineFactory<TServerEngine, TServerConfig>,
        clientEngineFactory: HttpClientEngineFactory<TClientConfig>
    ): BigFileResult = coroutineScope {
        val port = findFreePort()
        val expectedSize = if (config.useFileContent) testFile.length() else inMemoryData.size.toLong()

        val server = embeddedServer(serverEngineFactory, port = port) {
            routing {
                get("/bigfile") {
                    if (config.useFileContent) {
                        call.respond(LocalFileContent(testFile))
                    } else {
                        call.respondBytes(inMemoryData, ContentType.Application.OctetStream)
                    }
                }
            }
        }

        server.start(wait = false)

        try {
            HttpClient(clientEngineFactory).use { client ->
                val baseUrl = "http://127.0.0.1:$port"

                // Warmup
                println("Warmup phase (${config.warmupDuration})...")
                runTransferPhase(client, baseUrl, expectedSize, config.warmupDuration)

                // Measurement
                println("Measurement phase (${config.measurementDuration})...")
                runTransferPhase(client, baseUrl, expectedSize, config.measurementDuration)
            }
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
        }
    }

    private suspend fun runTransferPhase(
        client: HttpClient,
        baseUrl: String,
        expectedSize: Long,
        duration: Duration
    ): BigFileResult = coroutineScope {
        val bytesTransferred = AtomicLong(0)
        val transferCount = AtomicLong(0)

        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()

        val jobs = (1..config.concurrency).map {
            launch(Dispatchers.IO) {
                // Reusable buffer for discarding data
                val discardBuffer = ByteArray(64 * 1024)

                while (startMark.elapsedNow() < duration) {
                    try {
                        client.prepareGet("$baseUrl/bigfile").execute { response ->
                            val channel = response.bodyAsChannel()
                            var totalRead = 0L

                            // Read and discard - measure raw transfer speed
                            while (!channel.isClosedForRead) {
                                val read = channel.readAvailable(discardBuffer)
                                if (read > 0) {
                                    totalRead += read
                                }
                            }

                            bytesTransferred.addAndGet(totalRead)
                            transferCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        // Ignore errors during benchmark
                        System.err.println("Transfer error: ${e.message}")
                    }
                }
            }
        }

        jobs.forEach { it.join() }

        val actualDuration = startMark.elapsedNow()
        val totalBytes = bytesTransferred.get()
        val durationSeconds = actualDuration.inWholeMilliseconds / 1000.0

        val mbPerSecond = if (durationSeconds > 0) {
            (totalBytes / (1024.0 * 1024.0)) / durationSeconds
        } else {
            0.0
        }

        val gbps = mbPerSecond * 8 / 1000 // Convert MB/s to Gbps

        BigFileResult(
            megabytesPerSecond = mbPerSecond,
            gigabitsPerSecond = gbps,
            totalBytesTransferred = totalBytes,
            transferCount = transferCount.get(),
            measurementDuration = actualDuration
        )
    }

    private fun findFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }
}
