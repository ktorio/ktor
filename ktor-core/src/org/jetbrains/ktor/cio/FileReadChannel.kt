package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*
import java.nio.file.*

class FileReadChannel(val source: RandomAccessFile, val start: Long = 0, val endInclusive: Long = source.length() - 1) : RandomAccessReadChannel {
    private var initialSeek = false

    override val position: Long
        get() = source.filePointer

    override val size: Long
        get() = source.length()

    init {
        require(start >= 0L) { "start position shouldn't be negative but it is $start" }
        require(endInclusive <= source.length() - 1) { "endInclusive points to the position out of the file: file size = ${source.length()}, endInclusive = $endInclusive" }
    }

    suspend override fun read(dst: ByteBuffer): Int {
        if (!initialSeek) {
            seek(start)
        }

        val limit = Math.min(dst.remaining().toLong(), endInclusive - position + 1).toInt()
        if (limit <= 0)
            return -1
        dst.limit(dst.position() + limit)

        return source.channel.read(dst)
    }

    suspend override fun seek(position: Long) {
        require(position >= 0L) { "position should not be negative: $position" }
        require(position <= source.length()) { "position should not run out of the file range: $position !in [0, ${source.length()}]" }

        source.seek(position)
        initialSeek = true
    }

    override fun close() {
        source.close()
    }
}

fun Path.readChannel(start: Long = 0, endInclusive: Long = Files.size(this) - 1): FileReadChannel {
    return toFile().readChannel(start, endInclusive)
}

fun File.readChannel(start: Long = 0, endInclusive: Long = length() - 1): FileReadChannel {
    return FileReadChannel(RandomAccessFile(this, "r"), start, endInclusive)
}
