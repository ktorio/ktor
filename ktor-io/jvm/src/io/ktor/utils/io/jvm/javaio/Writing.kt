package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import java.io.*

/**
 * Copies up to [limit] bytes from [this] byte channel to [out] stream suspending on read channel
 * and blocking on output
 *
 * @return number of bytes copied
 */
public suspend fun ByteReadChannel.copyTo(out: OutputStream, limit: Long = Long.MAX_VALUE): Long {
    require(limit >= 0) { "Limit shouldn't be negative: $limit" }

    val buffer = ByteArrayPool.borrow()
    try {
        var copied = 0L
        val bufferSize = buffer.size.toLong()

        while (copied < limit) {
            val rc = readAvailable(buffer, 0, minOf(limit - copied, bufferSize).toInt())
            if (rc == -1) break
            if (rc > 0) {
                out.write(buffer, 0, rc)
                copied += rc
            }
        }

        return copied
    } finally {
        ByteArrayPool.recycle(buffer)
    }
}
