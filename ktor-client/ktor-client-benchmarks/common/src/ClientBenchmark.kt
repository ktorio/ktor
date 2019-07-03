/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.benchmarks

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import kotlinx.benchmark.*

@State(Scope.Benchmark)
internal class ClientDownloadBenchmark {
    lateinit var client: HttpClient


    @Param("Apache", "OkHttp", "Android", "CIO")// "Jetty")
    var zengineName: String = ""

    @Param("0", "1", "16", "32", "64", "256", "1024")
    var size: Int = 0

    @Setup
    fun start() {
        client = HttpClient(findEngine(zengineName))
    }

    @Benchmark
    fun download() = runBenchmark {
        client.download(size)
    }

    @Benchmark
    fun upload() = runBenchmark {
        client.upload(size)
    }

    @Benchmark
    fun echoStream() = runBenchmark {
        client.echo(size)
    }

    @TearDown
    fun stop() {
        client.close()
    }
}

private val testData = mutableMapOf(
    0 to ByteArray(0),
    1 to ByteArray(1 * 1024),
    16 to ByteArray(16 * 1024),
    32 to ByteArray(32 * 1024),
    32 to ByteArray(32 * 1024),
    64 to ByteArray(64 * 1024),
    256 to ByteArray(256 * 1024),
    1024 to ByteArray(1024 * 1024)
)

internal suspend inline fun HttpClient.download(size: Int) {
    val data = get<ByteArray>("$TEST_BENCHMARKS_SERVER/bytes?size=$size")
    check(data.size == size * 1024)
}

internal suspend inline fun HttpClient.upload(size: Int) {
    val uploaded = post<String>("$TEST_BENCHMARKS_SERVER/bytes") {
        body = testData[size]!!
    }
    check(uploaded.toInt() == size * 1024) { println("Expected ${size * 1024}, got $uploaded")}
}

internal suspend inline fun HttpClient.echo(size: Int) {
    val uploaded = post<ByteArray>("$TEST_BENCHMARKS_SERVER/echo?size=$size") {
        body = testData[size]!!
    }

    check(uploaded.size == size * 1024) { println("Expected ${size * 1024}, got ${uploaded.size}")}
}
