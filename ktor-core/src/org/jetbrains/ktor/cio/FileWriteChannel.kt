package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*

class FileWriteChannel(val target: RandomAccessFile) : WriteChannel {
    suspend override fun write(src: ByteBuffer) {
        target.write(src.array(), src.arrayOffset() + src.position(), src.limit())
    }

    suspend override fun flush() {
        target.fd.sync()
    }

    override fun close() {
        target.close()
    }
}

fun File.writeChannel(): FileWriteChannel {
    return FileWriteChannel(RandomAccessFile(this, "rw"))
}

