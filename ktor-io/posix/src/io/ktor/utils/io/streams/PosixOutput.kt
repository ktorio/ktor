package io.ktor.utils.io.streams

import kotlinx.cinterop.*
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.internal.utils.*
import platform.posix.*

/**
 * Create a blocking [Output] writing to the specified [fileDescriptor] using [write].
 */
@Suppress("FunctionName")
@ExperimentalIoApi
public fun Output(fileDescriptor: Int): Output = PosixFileDescriptorOutput(fileDescriptor)

/**
 * Create a blocking [Output] writing to the specified [file] instance using [fwrite].
 */
@Suppress("FunctionName")
@ExperimentalIoApi
public fun Output(file: CPointer<FILE>): Output = PosixFileInstanceOutput(file)

private class PosixFileDescriptorOutput(val fileDescriptor: Int) : AbstractOutput() {
    private var closed = false

    init {
        check(fileDescriptor >= 0) { "Illegal fileDescriptor: $fileDescriptor" }
        check(kx_internal_is_non_blocking(fileDescriptor) == 0) { "File descriptor is in O_NONBLOCK mode." }
    }

    override fun flush(source: Memory, offset: Int, length: Int) {
        val fileDescriptor = fileDescriptor
        val end = offset + length
        var currentOffset = offset

        while (currentOffset < end) {
            val result = write(fileDescriptor, source, currentOffset, end - currentOffset)
            if (result == 0) {
                throw PosixException.forErrno(posixFunctionName = "fwrite()").wrapIO()
            }
            currentOffset += result
        }
    }

    override fun closeDestination() {
        if (closed) return
        closed = true

        if (close(fileDescriptor) != 0) {
            val error = errno
            if (error != EBADF) { // EBADF is already closed or not opened
                throw PosixException.forErrno(error, posixFunctionName = "close()").wrapIO()
            }
        }
    }
}

private class PosixFileInstanceOutput(val file: CPointer<FILE>) : AbstractOutput() {
    private var closed = false

    override fun flush(source: Memory, offset: Int, length: Int) {
        val end = offset + length
        var currentOffset = offset

        while (currentOffset < end) {
            val result = fwrite(source, currentOffset, end - currentOffset, file)
            if (result == 0) {
                throw PosixException.forErrno(posixFunctionName = "fwrite()").wrapIO()
            }
            currentOffset += result
        }
    }

    override fun closeDestination() {
        if (closed) return
        closed = true

        if (fclose(file) != 0) {
            throw PosixException.forErrno(posixFunctionName = "fclose").wrapIO()
        }
    }
}
