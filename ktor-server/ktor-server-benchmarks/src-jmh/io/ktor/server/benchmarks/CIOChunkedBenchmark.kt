package io.ktor.server.benchmarks

import io.ktor.http.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.openjdk.jmh.annotations.*
import java.nio.ByteBuffer
import java.util.*
import kotlin.coroutines.experimental.*

@State(Scope.Benchmark)
class CIOChunkedBenchmark {
    private final val data: ByteBuffer = ByteBuffer.allocateDirect(8192)!!
    init {
        val rnd = ByteArray(8192)
        Random().nextBytes(rnd)
        data.duplicate().put(rnd)
    }

    @Benchmark
    fun encode() = runBlocking(Unconfined) {
        val bb: ByteBuffer = data.duplicate()

        val source = ByteReadChannel(bb)
        val chunked = ByteChannel()
        launch(coroutineContext) {
            chunked.discard()
        }

        encodeChunked(chunked, source)
        chunked.close()
    }
}

fun main(args: Array<String>) {
    benchmark(args) {
        run<CIOChunkedBenchmark>()
    }
}