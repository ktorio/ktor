package org.jetbrains.ktor.nio

import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*

class FileReadChannel(val fc: AsynchronousFileChannel, val start: Long = 0, val endInclusive: Long = fc.size() - 1, val preventClose: Boolean = false) : SeekableChannel {

    constructor(fc: AsynchronousFileChannel, range: LongRange = 0L .. fc.size() - 1, preventClose: Boolean = false) : this(fc, range.start, range.endInclusive, preventClose)

    private var currentHandler: AsyncHandler? = null

    init {
        require(start >= 0L) { "start position shouldn't be negative but it is $start"}
        require(endInclusive >= start) { "endInclusive shouldn't be less than start but start = $start, endInclusive = $endInclusive" }
        require(endInclusive <= fc.size() - 1) { "endInclusive points to the position out of the file: file size = ${fc.size()}, endInclusive = $endInclusive" }
    }

    override var position = start
        private set

    val range: LongRange
        get () = start .. endInclusive

    override fun seek(position: Long, handler: AsyncHandler) {
        require(position >= 0L) { "position should not be negative: $position" }
        require(position < fc.size()) { "position should not run out of the file range: $position !in [0, ${fc.size()})" }

        this.position = position
        handler.successEnd()
    }

    override fun close() {
        if (!preventClose) fc.close()
    }

    private val readHandler = object : CompletionHandler<Int, ByteBuffer> {
        override fun failed(exc: Throwable, attachment: ByteBuffer) {
            withHandler { it.failed(exc) }
        }

        override fun completed(rc: Int, attachment: ByteBuffer) {
            val dst = attachment

            if (rc == -1) {
                withHandler { it.successEnd() }
            } else {
                position += rc
                val overRead = Math.max(0L, position - endInclusive - 1)
                if (overRead > 0) {
                    require(overRead < Int.MAX_VALUE)
                    dst.position(dst.position() - overRead.toInt())
                }

                withHandler { it.success(rc - overRead.toInt()) }
            }
        }
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (position > endInclusive) {
            handler.successEnd()
            return
        }

        try {
            currentHandler = handler
            fc.read(dst, position, dst, readHandler)
        } catch (e: Throwable) {
            handler.failed(e)
        }
    }

    private inline fun withHandler(block: (AsyncHandler) -> Unit) {
        val handler = currentHandler
        currentHandler = null
        if (handler != null) {
            block(handler)
        }
    }
}

fun Path.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = Files.size(this) - 1) = FileReadChannel(AsynchronousFileChannel.open(this, StandardOpenOption.READ), start, endInclusive)
fun File.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = length() - 1) = toPath().asyncReadOnlyFileChannel(start, endInclusive)
