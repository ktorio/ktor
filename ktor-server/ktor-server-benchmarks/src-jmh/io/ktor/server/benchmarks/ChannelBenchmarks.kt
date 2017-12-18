package io.ktor.server.benchmarks

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.openjdk.jmh.annotations.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.file.*

@State(Scope.Benchmark)
class ChannelBenchmarks {
    private final val coreDirectory = File("../ktor-server-core").absoluteFile.normalize()
    private final val smallFile = File(coreDirectory, "build.gradle")
    private final val largeFile = File(coreDirectory, "build").walkTopDown().maxDepth(2).filter {
        it.name.startsWith("ktor-server-core") && it.name.endsWith("SNAPSHOT.jar")
    }.single()

    private val file = largeFile

    @Benchmark
    fun directReads(): Int {
        return file.readBytes().size
    }

    @Benchmark
    fun directStreamReads(): Int {
        return file.inputStream().use { it.readBytes().size }
    }

    @Benchmark
    fun asyncChannelReads(): Long {
        val channel = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)
        val buffer = ByteBuffer.allocate(8192)
        var position = 0L

        while (true) {
            buffer.clear()
            val count = channel.read(buffer, position).get()
            if (count == -1) {
                channel.close()
                return position
            }
            position += count
        }
    }

    @Benchmark
    fun readChannelReads(): Int = runBlocking {
        file.readChannel().readRemaining().let { val size = it.remaining; it.release(); size }
    }

    @Benchmark
    fun readChannelStreamReads(): Int = runBlocking {
        file.readChannel().toInputStream().readBytes().size
    }
}
/*
Small file:

ChannelBenchmarks.asyncChannelReads       thrpt   20   48.448 ±  0.415  ops/ms
ChannelBenchmarks.directReads             thrpt   20  119.661 ± 24.684  ops/ms
ChannelBenchmarks.directStreamReads       thrpt   20  111.091 ±  4.489  ops/ms
ChannelBenchmarks.readChannelReads        thrpt   20  116.175 ±  1.269  ops/ms
ChannelBenchmarks.readChannelStreamReads  thrpt   20  100.482 ±  1.433  ops/ms

Large file:

ChannelBenchmarks.asyncChannelReads       thrpt   20  1.486 ± 0.006  ops/ms
ChannelBenchmarks.directReads             thrpt   20  6.307 ± 0.117  ops/ms
ChannelBenchmarks.directStreamReads       thrpt   20  3.337 ± 0.054  ops/ms
ChannelBenchmarks.readChannelReads        thrpt   20  2.488 ± 0.040  ops/ms
ChannelBenchmarks.readChannelStreamReads  thrpt   20  2.064 ± 0.029  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 1
        run<ChannelBenchmarks>()
    }
}

