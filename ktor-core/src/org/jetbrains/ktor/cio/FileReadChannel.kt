package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*
import java.nio.file.*

class FileReadChannel(val source: RandomAccessFile, val start: Long = 0, val endInclusive: Long = source.length() - 1) : RandomAccessReadChannel {
    private var initialPositionSet = start == 0L

    override var position: Long = 0

    override val size: Long
        get() = endInclusive - start + 1

    init {
        require(start >= 0L) { "start position shouldn't be negative but it is $start" }
        require(endInclusive <= source.length() - 1) { "endInclusive points to the position out of the file: file size = ${source.length()}, endInclusive = $endInclusive" }
    }

    suspend override fun read(dst: ByteBuffer): Int {
        if (!initialPositionSet) {
            seek(0)
            initialPositionSet = true
        }

        val limit = Math.min(dst.remaining().toLong(), size - position).toInt()
        if (limit <= 0)
            return -1


        val count = source.read(dst.array(), dst.arrayOffset() + dst.position(), limit)
        dst.position(dst.position() + count)
        position += count
        return count
    }

    suspend override fun seek(position: Long) {
        require(position >= 0L) { "position should not be negative: $position" }
        require(position <= size) { "position should not run out of the file range: $position !in [0, ${source.length()}]" }

        source.seek(position + start)
        this.position = position
    }

    override fun close() {
        source.close()
    }
}

fun Path.readChannel(start: Long, endInclusive: Long) = toFile().readChannel(start, endInclusive)
fun Path.readChannel() = toFile().readChannel()

fun File.readChannel() = FileReadChannel(RandomAccessFile(this, "r"))
fun File.readChannel(start: Long, endInclusive: Long) = FileReadChannel(RandomAccessFile(this, "r"), start, endInclusive)

