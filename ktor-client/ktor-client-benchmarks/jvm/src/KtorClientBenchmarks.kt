package io.ktor.client.benchmarks

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import org.jetbrains.gradle.benchmarks.*

internal const val TEST_BENCHMARKS_SERVER = "http://127.0.0.1:8080/benchmarks"

@State(Scope.Benchmark)
internal abstract class KtorClientBenchmarks(
    private val factory: HttpClientEngineFactory<*>
) {
    lateinit var client: HttpClient

    @Setup
    fun start() {
        client = HttpClient(factory)
    }

    @Benchmark
    fun download1K() = runBenchmark {
        client.download(1)
    }

    @Benchmark
    fun download16K() = runBenchmark {
        client.download(16)
    }

    @Benchmark
    fun download32K() = runBenchmark {
        client.download(32)
    }

    @Benchmark
    fun download64K() = runBenchmark {
        client.download(64)
    }

    @Benchmark
    fun download256K() = runBenchmark {
        client.download(256)
    }

    @Benchmark
    fun download1024K() = runBenchmark {
        client.download(1024)
    }

    @TearDown
    fun stop() {
        client.close()
    }
}

internal suspend inline fun HttpClient.download(size: Int) {
    val data = get<ByteArray>("$TEST_BENCHMARKS_SERVER/bytes?size=$size")
    check(data.size == size * 1024)
}
