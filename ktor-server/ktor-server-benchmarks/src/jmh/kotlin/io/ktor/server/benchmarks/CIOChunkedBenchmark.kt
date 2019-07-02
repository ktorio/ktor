/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.benchmarks

import io.ktor.http.cio.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import org.openjdk.jmh.annotations.*
import java.nio.ByteBuffer
import java.util.*

@State(Scope.Benchmark)
class CIOChunkedBenchmark {
    private final val data: ByteBuffer = ByteBuffer.allocateDirect(8192)!!
    init {
        val rnd = ByteArray(8192)
        Random().nextBytes(rnd)
        data.duplicate().put(rnd)
    }

    @Benchmark
    fun encode() = runBlocking(Dispatchers.Unconfined) {
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
