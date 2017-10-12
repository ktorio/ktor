package io.ktor.server.benchmarks

import kotlinx.coroutines.experimental.*
import io.ktor.cio.*
import org.openjdk.jmh.annotations.*
import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*

@State(Scope.Benchmark)
class ChannelBenchmarks {
    val file = listOf(File("test/io/ktor/tests/nio/DeflaterReadChannelTest.kt"),
            File("ktor-server/ktor-server-core-tests/test/io/ktor/tests/nio/DeflaterReadChannelTest.kt")).first(File::exists)

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
        file.readChannel().use { it.copyTo(ByteBufferWriteChannel()) }
    }

    @Benchmark
    fun readChannelStreamReads(): Int = runBlocking {
        file.readChannel().use { it.toInputStream().readBytes().size }
    }
}
/*
ChannelBenchmarks.asyncChannelReads       thrpt   10   47.120 ±  1.486  ops/ms
ChannelBenchmarks.directReads             thrpt   10  111.603 ± 26.161  ops/ms
ChannelBenchmarks.directStreamReads       thrpt   10  103.636 ±  9.133  ops/ms
ChannelBenchmarks.readChannelReads        thrpt   10   54.903 ±  0.754  ops/ms
ChannelBenchmarks.readChannelStreamReads  thrpt   10   48.044 ±  1.214  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 1
        run<ChannelBenchmarks>()
    }
}